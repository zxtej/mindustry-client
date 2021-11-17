package mindustry.client.antigrief

import arc.*
import arc.func.*
import arc.math.geom.*
import arc.scene.event.*
import arc.scene.ui.*
import arc.scene.ui.layout.*
import arc.util.*
import mindustry.Vars.*
import mindustry.ai.types.*
import mindustry.client.ClientVars.*
import mindustry.client.Spectate.spectate
import mindustry.core.*
import mindustry.entities.units.UnitController
import mindustry.game.*
import mindustry.gen.*
import mindustry.gen.Unit
import mindustry.input.*
import mindustry.type.*
import mindustry.ui.*
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
    private lateinit var view: Button
    private lateinit var updateCons: Cons<Cell<Button>>

    init {
        val log : UnitLog? = trackedUnits.get(typeId).get(unit.id)
        if(syncing && log != null) {
            log.unit = this.unit
        } else {
            if(syncing || Time.timeSinceMillis(lastJoinTime) > 10000f) {
                bornTime = Instant.now()
            }
            trackedUnits.get(typeId).put(unit.id, this)
        }
    }


    companion object {
        val coordsRegex: Regex = "(\\([\\d.]*, [\\d.]*\\)) accessed by".toRegex()
        const val iconSize = 64f
        init {
            Events.on(EventType.UnitDeadEvent::class.java) {
                die(it.unit, Instant.now())
            }

            Events.on(EventType.UnitDespawnEvent::class.java) {
                despawn(it.unit, Instant.now())
            }
        }

        private fun die(unit: Unit, time: Instant) {
            trackedUnits.get(unit.type.id.toInt()).get(unit.id)?.die(time) ?: return
        }

        private fun despawn(unit: Unit, time: Instant) {
            trackedUnits.get(unit.type.id.toInt()).get(unit.id)?.despawn(time) ?: return
        }

        fun update() {
            trackedUnits.each { map ->
                map.values().forEach { it.update()}
            }
        }

        private fun getTimeAgo(time: Instant): String = UI.formatMinutesFromMillis(Time.timeSinceMillis(time.toEpochMilli()))
    }

    fun die(time: Instant) {
        update()
        deathTime = time
        if(prevController !== controller || (unit !== Nulls.unit && (prevController as? LogicAI)?.controller?.lastAccessed !== (controller as? LogicAI)?.controller?.lastAccessed)){
            interactor = unit.toInteractor()
            prevController = controller
        }
        unit = Nulls.unit
    }

    fun despawn(time: Instant) {
        prevController = controller
        x = unit.x
        y = unit.y
        flag = unit.flag
        deathTime = time
        unit = Nulls.unit
    }

    fun update() {
        if(!unit.isAdded) return // Nulls.unit also returns false for isAdded
        controller = unit.controller()
        x = unit.x
        y = unit.y
        flag = unit.flag
        if(prevController !== controller || (unit !== Nulls.unit && (prevController as? LogicAI)?.controller?.lastAccessed != (controller as? LogicAI)?.controller?.lastAccessed)){
            interactor = unit.toInteractor()
            controller = prevController
        }
    }

    fun getIdFlag() : String = if(isLogicControlled()) "$id ([gray]flag: [white]${flag.toInt()})" else id

    fun getController(isHovered: Boolean) : String {
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

    fun getView(logTable: Table, refresh: Boolean) {
        if(!refresh && this::view.isInitialized){
            logTable.add(view).self(updateCons).minHeight(0f)
            return
        }
        view = Button(Styles.cleari)
        (Cons { frame: Button ->
            frame.table { t: Table ->
                t.image(type.uiIcon).size(iconSize).growY().align(Align.right)
                t.table { t2: Table ->
                    t2.defaults().growX().left()
                    t2.labelWrap { getIdFlag() }
                    t2.row()
                    t2.button({ b: Button -> b.labelWrap{ getController(Core.input.shift()) }.left().grow() }, Styles.nonet, lambda@ {
                        if(!Core.input.shift() || !isLogicControlled()) return@lambda
                        (control.input as? DesktopInput)?.panning = true
                        lastSentPos.set(getControllerX(), getControllerY())
                        spectate(getLogicController()?.controller?: return@lambda)
                    }).disabled { !isLogicControlled() }
                    t2.row()
                    t2.button({ b: Button -> b.labelWrap{ getCoordsString() }.left().grow() }, Styles.nonet, {
                        if(Core.input.shift()) spectate(if(unit === Nulls.unit) Vec2(x, y) else unit)
                    })
                    t2.row()
                    t2.label { getBornTime() }
                    t2.row()
                    t2.label { getDeathTime() }
                    t2.row()
                }.pad(4f).width(370f - iconSize).growY()
            }
        }).get(view)

        updateCons = Cons { t: Cell<Button> ->
            t.update { frame: Button ->
                if (frame.height > t.minHeight()) t.minHeight(frame.height)
                val bottom = frame.localToStageCoordinates(Tmp.v1.set(0f, 0f)) //bottom left
                frame.touchable { if (bottom.y + frame.height < 0 || bottom.y > Core.graphics.height) Touchable.disabled else Touchable.enabled }
            }
        }

        logTable.add(view).self(updateCons)
    }
}