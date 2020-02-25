package mindustry.entities.units;

import arc.math.Mathf;
import mindustry.Vars;
import mindustry.content.Items;
import mindustry.entities.type.BaseUnit;
import mindustry.entities.type.TileEntity;
import mindustry.gen.Call;
import mindustry.type.Item;

import java.util.HashMap;

import static mindustry.Vars.*;

public class UnitDrops{
    private static HashMap<Item, Float> drops = new HashMap<>(); // item, dropchance

    public static void dropItems(BaseUnit unit){
        //items only dropped in waves for enemy team
        if(unit.getTeam() != state.rules.waveTeam){
            return;
        }

        drops.put(Items.copper, 0.5f);
        drops.put(Items.lead, 0.5f);
        drops.put(Items.graphite, 0.3f);
        drops.put(Items.silicon, 0.25f);
        drops.put(Items.titanium, 0.15f);
        drops.put(Items.metaglass, 0.1f);
        drops.put(Items.thorium, 0.05f);
        drops.put(Items.plastanium, 0.02f);
        drops.put(Items.surgealloy, 0.01f);
        drops.put(Items.phasefabric, 0.005f);

        TileEntity core = unit.getClosestEnemyCore();


        for(int i = 0; i < 3; i++){
            for(Item item : drops){ // TODO: make this entryset loop, use chance & item.
                if(Mathf.chance(0.03)){
                    int amount = Mathf.random(1, 40);
                    amount = core.tile.block().acceptStack(item, amount, core.tile, null);
                    if(amount > 0){
                        Call.transferItemTo(item, amount, unit.x + Mathf.range(2f), unit.y + Mathf.range(2f), core.tile);
                    }
                }
            }
        }
    }
}
