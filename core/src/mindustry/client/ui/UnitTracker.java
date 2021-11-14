package mindustry.client.ui;

import arc.*;
import arc.math.geom.*;
import arc.scene.*;
import arc.scene.event.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.client.*;
import mindustry.client.antigrief.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.input.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;

import static mindustry.Vars.*;
import static mindustry.client.ClientVars.*;

public class UnitTracker extends BaseDialog {
    private static final float tagh = 42f;
    private Runnable rebuildPane = () -> {}, rebuildTags = () -> {};
    private final Seq<UnitType> selectedTags = new Seq<>();
    private Table logTable;
    {
        Events.on(EventType.ClientLoadEvent.class, event -> Core.app.post(() -> { // help this is filthy
            rebuildPane = () -> {
                int cols = Math.max((int) (Core.graphics.getWidth() / Scl.scl(400)), 1);

                logTable.clear();
                logTable.defaults().margin(5f).pad(5f).growY();
                int i = 0;

                for (UnitType u : content.units()) {
                    if (selectedTags.any() && !selectedTags.contains(u)) continue;
                    for (UnitLog log : trackedUnits.get(u.id)) {
                        log.getView(logTable, false);
                        if (++i % cols == 0) {
                            logTable.row();
                        }
                    }
                }
            };

            rebuildPane.run();
        }));

        Events.on(EventType.WorldLoadEvent.class, event -> {
            if(!syncing) rebuildPane.run();
        });
    }

    public UnitTracker(){
        super("@client.unittracker");
        addCloseButton();
        shown(this::setup);
        onResize(this::setup);
        setup();
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
        cont.pane(t -> {
            logTable = t;
            t.top();
            rebuildPane.run();
        }).grow().scrollX(false);
    }
}
