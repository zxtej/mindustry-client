package mindustry.client.ui;

import arc.*;
import arc.func.*;
import arc.graphics.*;
import arc.math.*;
import arc.scene.*;
import arc.scene.actions.*;
import arc.scene.style.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.scene.ui.layout.Stack;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.logic.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;

import static mindustry.Vars.*;
import static mindustry.client.ClientVars.*;
import static mindustry.logic.LCanvas.tooltip;

public class UnitTracker extends BaseDialog {
    private static final float tagh = 42f, entryh = 60f;
    static Seq<String> acceptedFields = new Seq<>();
    static Boolf<String> fieldValidator = str -> acceptedFields.contains(str.trim());
    static Color accept = Colors.get("GREEN"), deny = Colors.get("RED");

    private Runnable rebuildPane = () -> {}, rebuildTags = () -> {}, rebuildCriteria;

    private final Seq<UnitType> selectedTags = new Seq<>();

    Table entryTable;
    Seq<String> filterTypes = new Seq<>();
    Seq<TextField> textFields = new Seq<>();
    Seq<Integer> selected = new Seq<>();
    Seq<SortEntry> sortEntries = new Seq<>();
    public int entries = 0;

    private Table logTable;
    {
        Events.on(EventType.ClientLoadEvent.class, event -> Core.app.post(() -> { // help this is filthy
            rebuildPane = () -> {
                int cols = Math.max((int) (Core.graphics.getWidth() / Scl.scl(400)), 1);

                logTable.clear();
                logTable.defaults().margin(5f).pad(5f).growY();
                int[] i = {0};

                for (UnitType u : content.units()) {
                    if (selectedTags.any() && !selectedTags.contains(u)) continue;
                    trackedUnits.get(u.id).values().forEach(log -> {
                        log.getView(logTable, false);
                        if (++i[0] % cols == 0) {
                            logTable.row();
                        }
                    });
                }
            };

            rebuildPane.run();

            acceptedFields.addAll(Vars.content.items().<String>map(i -> "@" + i.name));
            acceptedFields.addAll(new Seq<>(LAccess.senseable).<String>map(s -> "@" + s.name()));
        }));

        Events.on(EventType.WorldLoadEvent.class, event -> {
            if(!syncing) rebuildPane.run();
        });
    }
    {
        rebuildCriteria = () -> {
            entryTable.clear();
            if(entries == 0){
                entryTable.button("Add criteria", () -> {
                    new SortEntry().addLoc();
                    rebuildCriteria.run();
                }).growX().height(entryh);
            } else {
                sortEntries.each(se -> {
                    entryTable.add(se).expandY().fillX().align(Align.left);
                    entryTable.row();
                });
            }
        };
    }

    public UnitTracker(){
        super("@client.unittracker");
        addCloseButton();
        shown(this::setup);
        onResize(this::setup);
        setup();
        //hidden(clear everything);
    }

    void setup(){
        cont.top();
        cont.clear();

        cont.table(in -> {
            in.left();
            in.pane(Styles.nonePane, t -> {
                rebuildTags = () -> {
                    t.clearChildren();
                    t.left();

                    t.defaults().pad(2).height(tagh);
                    for(var tag : content.units()){
                        t.button(Fonts.getUnicodeStr(tag.name), Styles.togglet, () -> {
                            if(selectedTags.contains(tag)){
                                selectedTags.remove(tag);
                            }else{
                                selectedTags.add(tag);
                            }
                            rebuildPane.run();
                        }).checked(selectedTags.contains(tag)).with(c -> c.getLabel().setWrap(false));
                    }
                };
                rebuildTags.run();
            }).fillX().height(tagh).scrollY(false);
        }).height(tagh).fillX();

        cont.row();
        if(entryTable != null) entryTable.clear();
        entryTable = cont.table().growX().expandY().get();
//        for(int i=0 ; i < entries; i++){
//            new SortEntry().addLoc();
//        }
        rebuildCriteria.run();
        cont.row();
        cont.pane(t -> {
            logTable = t;
            t.top();
            rebuildPane.run();
        }).grow().scrollX(false);
    }

    class SortEntry extends Table{
        int index;

        private void build(){
            defaults().pad(0f, 5f, 0f, 5f);
            left();
            table(this::rebuildSortCriteria).expandY().align(Align.left);
            add().growX();
            button(Icon.add, Styles.cleari, () -> {
                new SortEntry().addLoc(index+1);
                rebuildCriteria.run();
            }).grow();
            button(Icon.cancel, Styles.cleari, () -> {
                removeLoc(index);
                rebuildCriteria.run();
            }).grow();
        }

        public Table addLoc(){
            return addLoc(entries);
        }

        private Table addLoc(int loc){
            index = Mathf.clamp(loc, 0, entries);
            for(int i = index; i < entries; i++){
                sortEntries.get(i).index++;
            }
            filterTypes.insert(index, "");
            textFields.insert(index, null);
            selected.insert(index, 0);
            sortEntries.insert(index, this);
            entries++;
            build();
            return this;
        }

        private void removeLoc(int loc){
            for(int i = loc+1; i < entries; i++){
                sortEntries.get(i).index--;
            }
            filterTypes.remove(loc);
            textFields.remove(loc);
            selected.remove(loc);
            sortEntries.remove(loc);
            entries--;
        }

        protected void showSelectTable(Button b, Cons2<Table, Runnable> hideCons){
            Table t = new Table(Tex.paneSolid){
                @Override
                public float getPrefHeight(){
                    return Math.min(super.getPrefHeight(), Core.graphics.getHeight());
                }

                @Override
                public float getPrefWidth(){
                    return Math.min(super.getPrefWidth(), Core.graphics.getWidth());
                }
            };
            t.margin(4);

            //triggers events behind the element to simulate deselection
            Element hitter = new Element();

            Runnable hide = () -> {
                Core.app.post(hitter::remove);
                t.actions(Actions.fadeOut(0.3f, Interp.fade), Actions.remove());
            };

            hitter.fillParent = true;
            hitter.tapped(hide);

            Core.scene.add(hitter);
            Core.scene.add(t);

            t.update(() -> {
                if(b.parent == null || !b.isDescendantOf(Core.scene.root)){
                    Core.app.post(() -> {
                        hitter.remove();
                        t.remove();
                    });
                    return;
                }

                b.localToStageCoordinates(Tmp.v1.set(b.getWidth()/2f, b.getHeight()/2f));
                t.setPosition(Tmp.v1.x, Tmp.v1.y, Align.center);
                if(t.getWidth() > Core.scene.getWidth()) t.setWidth(Core.graphics.getWidth());
                if(t.getHeight() > Core.scene.getHeight()) t.setHeight(Core.graphics.getHeight());
                t.keepInStage();
                t.invalidateHierarchy();
                t.pack();
            });
            t.actions(Actions.alpha(0), Actions.fadeIn(0.3f, Interp.fade));

            t.top().pane(inner -> {
                inner.top();
                hideCons.get(inner, hide);
            }).pad(0f).top().scrollX(false);

            t.pack();
        }

        private void rebuildSortCriteria(Table entry){
            entry.clearChildren();
            entry.align(Align.left | Align.center);
            entry.label(() -> "Criteria " + (index + 1) + ":");

            var field = entry.field(filterTypes.get(index), Styles.nodeField, str -> filterTypes.set(index, str)).size(288f, 40f).pad(2f).maxTextLength(LAssembler.maxTokenLength).padRight(0f).get();
            field.update(() -> field.setColor(field.getText()/*.isBlank()*/.trim().isEmpty()? Color.white : fieldValidator.get(field.getText())? accept : deny)); // String.isBlank is not available in JDK <11 bruh)
            textFields.set(index, field);

            entry.button(b -> {
                b.image(Icon.pencilSmall);
                b.clicked(() -> showSelectTable(b, (t2, hide) -> {
                    Table[] tables = {
                            //items
                            new Table(i -> {
                                i.left();
                                int c = 0;
                                for (Item item : content.items()) {
                                    if (!item.unlockedNow()) continue;
                                    i.button(new TextureRegionDrawable(item.uiIcon), Styles.cleari, iconSmall, () -> {
                                        stype("@" + item.name);
                                        hide.run();
                                    }).size(40f);

                                    if (++c % 6 == 0) i.row();
                                }
                            }),
                            //sensors
                            new Table(i -> {
                                for (LAccess sensor : LAccess.senseable) {
                                    i.button(sensor.name(), Styles.cleart, () -> {
                                        stype("@" + sensor.name());
                                        hide.run();
                                    }).size(240f, 40f).self(c -> tooltip(c, sensor)).row();
                                }
                            })
                    };
                    Drawable[] icons = {Icon.box, Icon.tree};
                    Stack stack = new Stack(tables[selected.get(index)]);
                    ButtonGroup<Button> group = new ButtonGroup<>();

                    for (int i = 0; i < tables.length; i++) {
                        int fi = i;

                        t2.button(icons[i], Styles.clearTogglei, () -> {
                            selected.set(index, fi);

                            stack.clearChildren();
                            stack.addChild(tables[selected.get(index)]);

                            t2.parent.parent.pack();
                            t2.parent.parent.invalidateHierarchy();
                        }).height(50f).growX().checked(selected.get(index) == fi).group(group);
                    }
                    t2.row();
                    t2.add(stack).colspan(3).width(240f).left();
                }));
            }, Styles.logict, () -> {
            }).size(40f).padLeft(-1).self(b -> { if(b.hasElement()) b.get().update(() -> b.get().setColor(field.color)); });
        }

        private void stype(String text){
            textFields.get(index).setText(text);
            filterTypes.set(index, text);
        }
    }
}
