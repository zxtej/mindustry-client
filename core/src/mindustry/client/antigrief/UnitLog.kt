package mindustry.client.antigrief

import arc.Events
import arc.util.Time
import mindustry.Vars
import mindustry.ai.types.LogicAI
import mindustry.client.ClientVars.*
import mindustry.client.utils.floor
import mindustry.core.UI
import mindustry.core.World
import mindustry.entities.units.UnitController
import mindustry.game.EventType
import mindustry.gen.*
import mindustry.gen.Unit
import mindustry.type.UnitType
import java.math.MathContext
import java.math.RoundingMode
import java.time.Instant

class UnitLog (var unit: Unit){
    val type: UnitType = unit.type
    private var controller: UnitController = unit.controller()
    private var prevController = controller
    private var interactor: Interactor = unit.toInteractor()
    val id: String = unit.id.toString()
    var x = unit.x
    var y = unit.y
    private val typeId = type.id.toInt()
    private var bornTime: Instant = Instant.EPOCH
    private var deathTime: Instant = Instant.EPOCH
    private val coordsRegex: Regex = "(\\([\\d.]*, [\\d.]*\\)) accessed by".toRegex()

    init {
        if(Vars.world.isGenerating){
            bornTime = Instant.now()
        }
        //do we need to check if it exists anyway?
        trackedUnits.get(typeId).add(this);
    }


    companion object {
        init {
            Events.on(EventType.UnitDeadEvent::class.java) {
                die(it.unit, Instant.now())
            }
        }

        private fun die(unit: Unit, time: Instant) {
            val log = trackedUnits.get(unit.type.id.toInt()).find { t -> t.unit == unit } ?: return
            log.update()
            log.unit = Nulls.unit
            log.deathTime = time
        }

        fun update() {
            trackedUnits.each {
                    it.each {
                        log -> log.update()
                    }}
        }
    }

    fun update() {
        if(unit == Nulls.unit) return
        controller = unit.controller()
        x = unit.x
        y = unit.y
    }

    fun getController(isHovered: Boolean) : String {
        if(prevController != controller || (prevController as? LogicAI)?.controller?.lastAccessed != (controller as? LogicAI)?.controller?.lastAccessed){
            interactor = unit.toInteractor()
            controller = prevController
        }
        if(isHovered && isLogicControlled()){
            val insertLoc = coordsRegex.find(interactor.name)?.groups?.get(1)?.range
            if(insertLoc != null && insertLoc.first > 0) return StringBuilder(interactor.name).insert(insertLoc.last + 1, "[]").insert(insertLoc.first, "[salmon]").toString()
        }
        return interactor.name
    }

    fun getControllerX() : Float = controller.unit().x
    fun getControllerY() : Float = controller.unit().y

    fun isLogicControlled() : Boolean = controller is LogicAI

    private val context: MathContext = MathContext(2, RoundingMode.HALF_UP)
    fun getCoordsString() : String {
        //return "At: [sky](%.2f, %.2f)".format(World.conv(x), World.conv(y)) //format slow
        return "At: [salmon](${(World.conv(x)*100f+0.5f).toInt()/100f}, ${(World.conv(y)*100f+0.5f).toInt()/100f})"
    }

    private fun getTimeAgo(time: Instant): String = UI.formatMinutesFromMillis(Time.timeSinceMillis(time.toEpochMilli()))

    fun getBornTime() : String {
        if(bornTime == Instant.EPOCH) return ""
        return "Created " + getTimeAgo(bornTime) + " ago"
    }

    fun getDeathTime() : String {
        if(deathTime == Instant.EPOCH) return ""
        return "Died " + getTimeAgo(deathTime) + " ago"
    }

}