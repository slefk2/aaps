package app.aaps.core.main.extensions

import app.aaps.core.interfaces.aps.AutosensResult
import app.aaps.core.interfaces.insulin.Insulin
import app.aaps.core.interfaces.iob.IobTotal
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.T
import app.aaps.database.entities.Bolus
import app.aaps.database.entities.TemporaryBasal
import app.aaps.database.entities.interfaces.end
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.roundToInt

fun TemporaryBasal.getPassedDurationToTimeInMinutes(time: Long): Int =
    ((min(time, end) - timestamp) / 60.0 / 1000).roundToInt()

val TemporaryBasal.plannedRemainingMinutes: Int
    get() = max(round((end - System.currentTimeMillis()) / 1000.0 / 60).toInt(), 0)

fun TemporaryBasal.convertedToAbsolute(time: Long, profile: Profile): Double =
    if (isAbsolute) rate
    else profile.getBasal(time) * rate / 100

fun TemporaryBasal.convertedToPercent(time: Long, profile: Profile): Int =
    if (!isAbsolute) rate.toInt()
    else (rate / profile.getBasal(time) * 100).toInt()

private fun TemporaryBasal.netExtendedRate(profile: Profile) = rate - profile.getBasal(timestamp)
val TemporaryBasal.durationInMinutes
    get() = T.msecs(duration).mins()

fun TemporaryBasal.toStringFull(profile: Profile, dateUtil: DateUtil, rh:ResourceHelper): String {
    val commonPrefix = dateUtil.timeString(timestamp) + " " + getPassedDurationToTimeInMinutes(dateUtil.now()) + "/" + durationInMinutes + "'"
    return when {
        type == TemporaryBasal.Type.FAKE_EXTENDED -> {
            rh.gs(app.aaps.core.ui.R.string.temporary_basal_fake_extended, rate, netExtendedRate(profile), commonPrefix)
        }

        isAbsolute                                -> {
            rh.gs(app.aaps.core.ui.R.string.temporary_basal_absolute, rate, commonPrefix)
        }

        else                                      -> { // percent
            rate.toString() + "% @" + commonPrefix
        }
    }
}

fun TemporaryBasal.toStringShort(rh: ResourceHelper): String =
    if (isAbsolute || type == TemporaryBasal.Type.FAKE_EXTENDED) rh.gs(app.aaps.core.ui.R.string.pump_base_basal_rate, rate)
    else rh.gs(app.aaps.core.ui.R.string.formatPercent, rate)

fun TemporaryBasal.iobCalc(time: Long, profile: Profile, insulinInterface: Insulin): IobTotal {
    if (!isValid) return IobTotal(time)
    val result = IobTotal(time)
    val realDuration = getPassedDurationToTimeInMinutes(time)
    var netBasalAmount = 0.0
    if (realDuration > 0) {
        var netBasalRate: Double
        val dia = profile.dia
        val diaAgo = time - dia * 60 * 60 * 1000
        val aboutFiveMinIntervals = ceil(realDuration / 5.0).toInt()
        val tempBolusSpacing = realDuration / aboutFiveMinIntervals.toDouble()
        for (j in 0L until aboutFiveMinIntervals) {
            // find middle of the interval
            val calcDate = (timestamp + j * tempBolusSpacing * 60 * 1000 + 0.5 * tempBolusSpacing * 60 * 1000).toLong()
            val basalRate = profile.getBasal(calcDate)
            netBasalRate = if (isAbsolute) {
                rate - basalRate
            } else {
                (rate - 100) / 100.0 * basalRate
            }
            if (calcDate > diaAgo && calcDate <= time) {
                val tempBolusSize = netBasalRate * tempBolusSpacing / 60.0
                netBasalAmount += tempBolusSize
                val tempBolusPart = Bolus(
                    timestamp = calcDate,
                    amount = tempBolusSize,
                    type = Bolus.Type.NORMAL
                )
                val aIOB = insulinInterface.iobCalcForTreatment(tempBolusPart, time, dia)
                result.basaliob += aIOB.iobContrib
                result.activity += aIOB.activityContrib
                result.netbasalinsulin += tempBolusPart.amount
                if (tempBolusPart.amount > 0) {
                    result.hightempinsulin += tempBolusPart.amount
                }
            }
        }
    }
    result.netInsulin = netBasalAmount
    return result
}

fun TemporaryBasal.iobCalc(
    time: Long,
    profile: Profile,
    lastAutosensResult: AutosensResult,
    exerciseMode: Boolean,
    halfBasalExerciseTarget: Int,
    isTempTarget: Boolean,
    insulinInterface: Insulin
): IobTotal {
    if (!isValid) return IobTotal(time)
    val result = IobTotal(time)
    val realDuration = getPassedDurationToTimeInMinutes(time)
    var netBasalAmount = 0.0
    var sensitivityRatio = lastAutosensResult.ratio
    val normalTarget = 100.0
    if (exerciseMode && isTempTarget && profile.getTargetMgdl() >= normalTarget + 5) {
        // w/ target 100, temp target 110 = .89, 120 = 0.8, 140 = 0.67, 160 = .57, and 200 = .44
        // e.g.: Sensitivity ratio set to 0.8 based on temp target of 120; Adjusting basal from 1.65 to 1.35; ISF from 58.9 to 73.6
        val c = halfBasalExerciseTarget - normalTarget
        sensitivityRatio = c / (c + profile.getTargetMgdl() - normalTarget)
    }
    if (realDuration > 0) {
        var netBasalRate: Double
        val dia = profile.dia
        val diaAgo = time - dia * 60 * 60 * 1000
        val aboutFiveMinIntervals = ceil(realDuration / 5.0).toInt()
        val tempBolusSpacing = realDuration / aboutFiveMinIntervals.toDouble()
        for (j in 0L until aboutFiveMinIntervals) {
            // find middle of the interval
            val calcDate = (timestamp + j * tempBolusSpacing * 60 * 1000 + 0.5 * tempBolusSpacing * 60 * 1000).toLong()
            var basalRate = profile.getBasal(calcDate)
            basalRate *= sensitivityRatio
            netBasalRate = if (isAbsolute) {
                rate - basalRate
            } else {
                val abs: Double = rate / 100.0 * profile.getBasal(calcDate)
                abs - basalRate
            }
            if (calcDate > diaAgo && calcDate <= time) {
                val tempBolusSize = netBasalRate * tempBolusSpacing / 60.0
                netBasalAmount += tempBolusSize
                val tempBolusPart = Bolus(
                    timestamp = calcDate,
                    amount = tempBolusSize,
                    type = Bolus.Type.NORMAL
                )
                val aIOB = insulinInterface.iobCalcForTreatment(tempBolusPart, time, dia)
                result.basaliob += aIOB.iobContrib
                result.activity += aIOB.activityContrib
                result.netbasalinsulin += tempBolusPart.amount
                if (tempBolusPart.amount > 0) {
                    result.hightempinsulin += tempBolusPart.amount
                }
            }
        }
    }
    result.netInsulin = netBasalAmount
    return result
}
