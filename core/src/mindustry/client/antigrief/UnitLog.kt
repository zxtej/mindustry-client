package mindustry.client.antigrief

import arc.Events
import arc.util.Time
import mindustry.Vars
import mindustry.ai.types.LogicAI
import mindustry.client.ClientVars.*
import mindustry.core.UI
import mindustry.core.World
import mindustry.entities.units.UnitController
import mindustry.game.EventType
import mindustry.gen.*
import mindustry.gen.Unit
import mindustry.type.UnitType
import java.time.Instant

class UnitLog (var unit: Unit){
    val type: UnitType = unit.type
    private var controller: UnitController = unit.controller()
    private var prevController = controller
    private var interactor: Interactor = unit.toInteractor()
    val id: String = unit.id.toString()
    var x = unit.x
    var y = unit.y
    var flag = unit.flag
    private val typeId = type.id.toInt()
    private var bornTime: Instant = Instant.EPOCH
    private var deathTime: Instant = Instant.EPOCH
    private val coordsRegex: Regex = "(\\([\\d.]*, [\\d.]*\\)) accessed by".toRegex()

    init {
        if(Vars.world.isGenerating || Vars.netClient.isConnecting) {
            bornTime = Instant.now()
        }
        trackedUnits.get(typeId).add(this)
    }


    companion object {
        init {
            Events.on(EventType.UnitDeadEvent::class.java) {
                die(it.unit, Instant.now())
            }

            Events.on(EventType.UnitDespawnEvent::class.java) {
                despawn(it.unit, Instant.now())
            }
        }

        private fun die(unit: Unit, time: Instant) {
            val log = trackedUnits.get(unit.type.id.toInt()).find { t -> t.unit == unit } ?: return
            log.die(time)
        }

        private fun despawn(unit: Unit, time: Instant) {
            val log = trackedUnits.get(unit.type.id.toInt()).find { t -> t.unit == unit } ?: return
            log.despawn(time)
        }

        fun update() {
            trackedUnits.each {
                    it.each {
                        log -> log.update()
                    }}
        }

        private fun getTimeAgo(time: Instant): String = UI.formatMinutesFromMillis(Time.timeSinceMillis(time.toEpochMilli()))
    }

    fun die(time: Instant) {
        update()
        deathTime = time
        if(prevController != controller || (unit != Nulls.unit && (prevController as? LogicAI)?.controller?.lastAccessed != (controller as? LogicAI)?.controller?.lastAccessed)){
            interactor = unit.toInteractor()
            prevController = controller
        }
        unit = Nulls.unit
    }

    fun despawn(time: Instant) {
        x = unit.x
        y = unit.y
        flag = unit.flag
        deathTime = time
        unit = Nulls.unit
    }

    fun update() {
        if(unit == Nulls.unit) return
        controller = unit.controller()
        x = unit.x
        y = unit.y
        flag = unit.flag
    }

    fun getIdFlag() : String = if(isLogicControlled()) "$id ([gray]flag: [white]${flag.toInt()})" else id

    fun getController(isHovered: Boolean) : String {
        if(prevController != controller || (unit != Nulls.unit && (prevController as? LogicAI)?.controller?.lastAccessed != (controller as? LogicAI)?.controller?.lastAccessed)){
            interactor = unit.toInteractor()
            controller = prevController
        }
        if(isHovered && isLogicControlled()){
            val insertLoc = coordsRegex.find(interactor.name)?.groups?.get(1)?.range
            if(insertLoc != null && insertLoc.first > 0) return StringBuilder(interactor.name).insert(insertLoc.last + 1, "[]").insert(insertLoc.first, "[salmon]").toString()
        }
        return interactor.name
    }

    fun getControllerX() : Float = (controller as? LogicAI)?.controller?.x ?: controller.unit().x
    fun getControllerY() : Float = (controller as? LogicAI)?.controller?.y ?: controller.unit().y
    fun getLogicController() : LogicAI? = controller as? LogicAI

    fun isLogicControlled() : Boolean = controller is LogicAI

    fun getCoordsString() : String {
        //return "At: [sky](%.2f, %.2f)".format(World.conv(x), World.conv(y)) //format slow
        return "At: [salmon](${(World.conv(x)*100f+0.5f).toInt()/100f}, ${(World.conv(y)*100f+0.5f).toInt()/100f})"
    }

    fun getBornTime() : String {
        if(bornTime == Instant.EPOCH) return "[darkgray]Created:"
        return "Created " + getTimeAgo(bornTime) + " ago"
    }

    fun getDeathTime() : String {
        if(deathTime == Instant.EPOCH) return "[darkgray]Died:"
        return "Died " + getTimeAgo(deathTime) + " ago"
    }

}