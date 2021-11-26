package mindustry.client.antigrief

import arc.*
import arc.func.*
import arc.math.geom.*
import arc.scene.Element
import arc.scene.event.*
import arc.scene.ui.*
import arc.scene.ui.layout.*
import arc.struct.Seq
import arc.util.*
import mindustry.Vars.*
import mindustry.ai.types.*
import mindustry.client.ClientVars.*
import mindustry.client.Spectate.spectate
import mindustry.client.utils.Pair
import mindustry.core.*
import mindustry.ctype.Content
import mindustry.entities.units.UnitController
import mindustry.game.*
import mindustry.gen.*
import mindustry.gen.Unit
import mindustry.input.*
import mindustry.logic.GlobalConstants
import mindustry.logic.LAccess
import mindustry.logic.Senseable
import mindustry.type.*
import mindustry.ui.*
import java.time.Instant

class UnitLog (var unit: Unit){
    //TODO: blockUnitUnit for thing
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
    private lateinit var dudView: Element
    private lateinit var updateCons: Cons<Cell<Button>>
    private val senseSnapshotString: StringBuilder = StringBuilder("");
    @JvmField val senseSnapshot: Seq<Double> = Seq()

    init {
        synchronized (trackedUnits) {
            val log: UnitLog? = trackedUnits.get(typeId).get(unit.id)
            if (syncing && log != null) {
                log.unit = this.unit
            } else {
                if (syncing || Time.timeSinceMillis(lastJoinTime) > 10000f) {
                    bornTime = Instant.now()
                }
                trackedUnits.get(typeId).put(unit.id, this)
            }
        }
    }


    companion object {
        var viewHeight: Float = 0f
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
            synchronized (trackedUnits) {
                trackedUnits.each { map ->
                    map.values().forEach { it.update() }
                }
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
        synchronized (this) {
            controller = unit.controller()
            x = unit.x
            y = unit.y
            flag = unit.flag
            if (prevController !== controller || (unit !== Nulls.unit && (prevController as? LogicAI)?.controller?.lastAccessed != (controller as? LogicAI)?.controller?.lastAccessed)) {
                interactor = unit.toInteractor()
                prevController = controller
            }
        }
    }

    fun sense(v: Any) : Double {
//        if(v is Content) return unit.sense(v)
//        if(v is LAccess){
//            if(unit == Nulls.unit) return nullSense(v)
//            val value: Any = unit.senseObject(v)
//            return if(value == Senseable.noSensed){
//                unit.sense(v)
//            } else value.hashCode().toDouble()
//        }
//        return 0.0
        if(v is Content) return unit.sense(v)
        if(v !is LAccess) return 0.0
        synchronized (this) {
            return when (v) {
                LAccess.type -> typeId
                LAccess.x -> World.conv(x)
                LAccess.y -> World.conv(y)
                LAccess.size -> type.hitSize / tilesize
                LAccess.range -> type.range
                LAccess.flag -> flag
                LAccess.controller -> controller.hashCode()
                LAccess.controlled -> when (controller) {
                    is LogicAI -> GlobalConstants.ctrlProcessor
                    is Player -> GlobalConstants.ctrlPlayer
                    is FormationAI -> GlobalConstants.ctrlFormation
                    else -> 0.0
                }
                LAccess.team -> player.team().hashCode()
                else -> {
                    val value = unit.senseObject(v)
                    if (value === Senseable.noSensed) {
                        unit.sense(v)
                    } else value.hashCode()
                }
            }.toDouble()
        }
    }

    fun updateSnapshotText() {
        synchronized (senseSnapshotString) {
            senseSnapshotString.clear()
            for (i in 0 until senseSnapshot.size) {
                senseSnapshotString.append(ui.unitTracker.sortEntriesTemp.get(i).accessVariable.name).append(": ")
                    .append((senseSnapshot.get(i) * 100f + 0.5f).toInt().div(100f)).append("\n")
            }
            senseSnapshotString.removeSuffix("\n")
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

    fun getView(logTable: Table, refresh: Boolean) : Pair<Cell<Button>, Cons<Cell<Button>>>/*Pair<out Element, Cons<out Cell<out Element>>>*/ {
        if(!refresh && this::view.isInitialized){
            return Pair(logTable.add(view), updateCons)
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
                    t2.label { synchronized(senseSnapshotString) { senseSnapshotString.toString() }}.with {l -> l.height = if (l.text.isEmpty()) 0f else l.prefHeight}
                    t2.row()
                }.pad(4f).width(370f - iconSize).growY()
            }
        }).get(view)

        updateCons = Cons { t: Cell<Button> ->
            t.update { frame: Button ->
                if (frame.height > viewHeight) viewHeight = frame.height
                t.minHeight(viewHeight)
                val bottom = frame.localToStageCoordinates(Tmp.v1.set(0f, 0f)) //bottom left
                frame.touchable { if (bottom.y + frame.height < 0 || bottom.y > Core.graphics.height) Touchable.disabled else Touchable.enabled }
            }
        }

        return Pair(logTable.add(view), updateCons)
    }
}