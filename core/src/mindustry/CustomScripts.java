package mindustry;

import arc.*;
import arc.func.*;
import arc.util.async.*;
import mindustry.entities.units.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;

public class CustomScripts {
    public void set(boolean f) {}

    public static class onCommandCenterChange extends CustomScripts {
        static final Class<CommandBeforeAfterEvent> listening = CommandBeforeAfterEvent.class;
        private Cons<CommandBeforeAfterEvent> onChange;
        public boolean enabled = false;

        onCommandCenterChange() {
            init();
            Events.on(listening, onChange); // cannot be accessed from within js console
        }

        @Override
        public void set(boolean f){
            enabled = f;
        }
        public void remove() {
            Events.on(listening, onChange);
        }

        private void init() {
            onChange = e -> Threads.thread(() -> {
                Threads.sleep(50);
                if (!enabled) return;
                if (e.command_aft != UnitCommand.attack) return;
                Call.tileConfig(Vars.player, e.tile, e.command_bef);
            });
        }
    }


}
