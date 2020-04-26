package info.nightscout.androidaps.plugins.TuneProfile;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.Date;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.ProfileStore;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.CareportalEvent;
import info.nightscout.androidaps.db.ExtendedBolus;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.interfaces.InsulinInterface;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.treatments.Treatment;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.Round;
import info.nightscout.androidaps.utils.SP;
import info.nightscout.androidaps.utils.SafeParse;

public class Opts {
    public static List<Treatment> treatments;
    public static Profile profile;
    public static Profile pumpprofile;
    public List<BgReading> glucose;
    public List<TemporaryBasal> pumpHistory;
    public List<ExtendedBolus> pumpExtBolusHistory;
    public List<TemporaryBasal> pumpTempBasalHistory;
    public long start;
    public long end;
    public boolean categorize_uam_as_basal;
    public boolean tune_insulin_curve;

    // on each loop glucose containts only one day BG Value
    public JSONArray glucosetoJSON()  {
        JSONArray glucoseJson = new JSONArray();
        Date now = new Date(System.currentTimeMillis());
        int utcOffset = (int) ((DateUtil.fromISODateString(DateUtil.toISOString(now,null,null)).getTime()  - DateUtil.fromISODateString(DateUtil.toISOString(now)).getTime()) / (60 * 1000));

        try {
            for (BgReading bgreading:glucose ) {
                JSONObject bgjson = new JSONObject();
                bgjson.put("_id",bgreading._id);
                bgjson.put("date",bgreading.date);
                bgjson.put("dateString", DateUtil.toISOAsUTC(bgreading.date));
                bgjson.put("sgv",bgreading.value);
                bgjson.put("direction",bgreading.direction);
                bgjson.put("type","sgv");
                bgjson.put("systime", DateUtil.toISOAsUTC(bgreading.date));
                bgjson.put("utcOffset", utcOffset);
                glucoseJson.put(bgjson);
            }
        } catch (JSONException e) {}
        return glucoseJson;
    }

    /*
    //For treatment export, add starttime and endtime to export dedicated files for each loop
    public JSONArray pumpHistorytoJSON(long starttime, long endtime)  {
        JSONArray json = new JSONArray();
        try {
            for (CareportalEvent cp:pumpHistory ) {
                JSONObject cPjson = new JSONObject();

                if(cp.date >= starttime && cp.date <= endtime && cp.isValid) {
                    cPjson.put("_id", cp._id);
                    cPjson.put("eventType",cp.eventType);
                    cPjson.put("date",cp.date);
                    cPjson.put("dateString",DateUtil.toISOAsUTC(cp.date));
                    JSONObject object = new JSONObject(cp.json);
                    Iterator it = object.keys();
                    while (it.hasNext()) {
                        String key = (String)it.next();
                        cPjson.put(key, object.get(key));
                    }
                }
                json.put(cPjson);
            }
        } catch (JSONException e) {}

        return json;
    }
    */

    //For treatment export, add starttime and endtime to export dedicated files for each loop
    public JSONArray extBolustoJSON(long starttime, long endtime)  {
        JSONArray json = new JSONArray();
        try {
            for (ExtendedBolus cp:pumpExtBolusHistory ) {
                JSONObject cPjson = new JSONObject();

                if(cp.date >= starttime && cp.date <= endtime && cp.isValid) {
                    cPjson.put("_id", cp._id);
                    cPjson.put("eventType","Extended Bolus");
                    cPjson.put("date",cp.date);
                    cPjson.put("dateString",DateUtil.toISOAsUTC(cp.date));
                    cPjson.put("insulin",cp.insulin);
                    cPjson.put("insulinrate",cp.absoluteRate());
                    cPjson.put("realDuration",cp.getRealDuration());
                }
                json.put(cPjson);
            }
        } catch (JSONException e) {}

        return json;
    }

    public JSONArray pumpHistorytoJSON()  {
        return tempBasaltoJSON(pumpHistory);
    }


    public JSONArray pumpTempBasalHistorytoJSON()  {
        return tempBasaltoJSON(pumpTempBasalHistory);
    }

    public JSONArray treatmentstoJSON()  {
        JSONArray json = new JSONArray();

        try {
            for (Treatment cp:treatments ) {
                JSONObject cPjson = new JSONObject();
                String eventType = "";
                if(cp.insulin > 0 && cp.carbs > 0)
                    eventType = "Bolus Wizard";
                else if (cp.carbs > 0)
                    eventType = "Carb Correction";
                else
                    eventType = "Correction Bolus";

                if(cp.isValid) {
                    cPjson.put("_id", cp._id);
                    cPjson.put("eventType",eventType);
                    cPjson.put("date",cp.date);
                    cPjson.put("dateString",DateUtil.toISOAsUTC(cp.date));
                    cPjson.put("insulin",cp.insulin);
                    cPjson.put("carbs",cp.carbs);
                    cPjson.put("isSMB",cp.isSMB);
                }
                json.put(cPjson);
            }
        } catch (JSONException e) {}

        return json;
    }


    private JSONArray tempBasaltoJSON(List<TemporaryBasal> listTempBasals)  {
        JSONArray json = new JSONArray();
        try {
            for (TemporaryBasal cp:listTempBasals ) {
                JSONObject cPjson = new JSONObject();

                if(cp.isValid) {
                    cPjson.put("_id", cp._id);
                    cPjson.put("eventType","Temp Basal");
                    cPjson.put("date",cp.date);
                    cPjson.put("dateString",DateUtil.toISOAsUTC(cp.date));
                    cPjson.put("absolute",cp.absoluteRate);
                    cPjson.put("rate",cp.absoluteRate);
                    cPjson.put("percentrate",cp.percentRate);
                    cPjson.put("percentrate",cp.percentRate);
                    cPjson.put("durationInMinutes",cp.durationInMinutes);
                    cPjson.put("duration",cp.getRealDuration());
                    cPjson.put("isEnding",cp.isEndingEvent());
                    cPjson.put("isFakeExtended",cp.isFakeExtended);
                }
                json.put(cPjson);
            }
        } catch (JSONException e) {}

        return json;
    }


    public JSONObject profiletoOrefJSON()  {
        // Create a json profile with oref0 format
        // Include min_5m_carbimpact, insulin type, single value for carb_ratio and isf
        JSONObject json = new JSONObject();
        JSONObject store = new JSONObject();
        JSONObject convertedProfile = new JSONObject();
        int basalIncrement = 60 ;
        int secondfrommidnight = 60 * 60;
        InsulinInterface insulinInterface = ConfigBuilderPlugin.getPlugin().getActiveInsulin();

        try {
            json.put("min_5m_carbimpact",SP.getDouble("openapsama_min_5m_carbimpact", 3.0));
            json.put("dia", profile.getDia());

            JSONArray basals = new JSONArray();
            for (int h = 0; h < 24; h++) {
                String time;
                time = (h<10 ? "0"+ h : h)  + ":00:00";
                basals.put(new JSONObject().put("start", time).put("minutes", h * basalIncrement).put("rate", profile.getBasal(h*secondfrommidnight)));
            };
            json.put("basalprofile", basals);
            int isfvalue = (int) profile.getIsfMgdl();
            json.put("isfProfile",new JSONObject().put("sensitivities",new JSONArray().put(new JSONObject().put("i",0).put("start","00:00:00").put("sensitivity",isfvalue).put("offset",0).put("x",0).put("endoffset",1440))));
            // json.put("carbratio", new JSONArray().put(new JSONObject().put("time", "00:00").put("timeAsSeconds", 0).put("value", previousResult.optDouble("carb_ratio", 0d))));
            json.put("carb_ratio", profile.getIc());
            json.put("autosens_max", SafeParse.stringToDouble(MainApp.gs(R.string.key_openapsama_autosens_max)));
            json.put("autosens_min", SafeParse.stringToDouble(MainApp.gs(R.string.key_openapsama_autosens_min)));
            if (insulinInterface.getId() == InsulinInterface.OREF_ULTRA_RAPID_ACTING)
                json.put("curve","ultra-rapid");
            else if (insulinInterface.getId() == InsulinInterface.OREF_RAPID_ACTING)
                json.put("curve","rapid-acting");
            else if (insulinInterface.getId() == InsulinInterface.OREF_FREE_PEAK) {
                json.put("curve", "bilinear");
                json.put("insulinpeaktime",SP.getInt(MainApp.gs(R.string.key_insulin_oref_peak),75));
            }

        } catch (JSONException e) {}

        return json;
    }

    public synchronized Double getProfileBasal(Integer hour){
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);

        return profile.getBasal(c.getTimeInMillis());
    }

    public synchronized Double getPumpProfileBasal(Integer hour){
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);

        return profile.getBasal(c.getTimeInMillis());
    }
}
