package info.nightscout.androidaps.plugins.general.autotune;

import android.app.Activity;
import android.os.Bundle;

import dagger.android.support.DaggerFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;


//2 unknown imports disabled by philoul to build AAPS
//import butterknife.BindView;
//import butterknife.OnClick;
import java.util.Date;

import javax.inject.Inject;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.interfaces.ProfileFunction;
import info.nightscout.androidaps.interfaces.ProfileStore;
import info.nightscout.androidaps.plugins.general.autotune.data.ATProfile;
import info.nightscout.androidaps.plugins.profile.ns.NSProfilePlugin;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.sharedPreferences.SP;

/**
 * Created by Rumen Georgiev on 1/29/2018.
 * Rebase with current dev by philoul on 03/02/2020
 */

/* Todo: Reset results field and Switch/Copy button visibility when
*       Nb of days is changed
* Memorise NbDay of lastRun (inconsistencies if you leave AutotunePlugin and enter again in it (results of calculation still shown but default nb of days selected
* Add rxBus, and event management to update field results during calculation (calculation as to be in dedicated thread
* Add Copy to localPlugin button (to allow modification of tuned profile before ProfileSwitch
* Enable Direct ProfileSwitch button (and probably refactor UI)
*/
public class AutotuneFragment extends DaggerFragment implements View.OnClickListener {
    @Inject NSProfilePlugin nsProfilePlugin;
    @Inject AutotunePlugin autotunePlugin;
    @Inject SP sp;
    @Inject DateUtil dateUtil;
    @Inject ProfileFunction profileFunction;
    @Inject ResourceHelper resourceHelper;

    public AutotuneFragment() {super();}

    Button autotuneRunButton;
// disabled by philoul to build AAPS
//    @BindView(R.id.tune_profileswitch)
    Button autotuneProfileSwitchButton;
    Button autotuneCopyLocalButton;
    TextView warningView;
    TextView resultView;
    TextView lastRunView;
    EditText tune_days;
    //autotune tuneProfile = new autotune();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        try {
            View view = inflater.inflate(R.layout.autotune_fragment, container, false);

            warningView = (TextView) view.findViewById(R.id.tune_warning);
            resultView = (TextView) view.findViewById(R.id.tune_result);
            lastRunView = (TextView) view.findViewById(R.id.tune_lastrun);
            autotuneRunButton = (Button) view.findViewById(R.id.autotune_run);
            autotuneCopyLocalButton=(Button) view.findViewById(R.id.autotune_copylocal);
            autotuneProfileSwitchButton = (Button) view.findViewById(R.id.autotune_profileswitch);
            tune_days = (EditText) view.findViewById(R.id.tune_days);
            autotuneRunButton.setOnClickListener(this);
            autotuneCopyLocalButton.setVisibility(View.GONE);
            autotuneCopyLocalButton.setOnClickListener(this);
            autotuneProfileSwitchButton.setVisibility(View.GONE);
            autotuneProfileSwitchButton.setOnClickListener(this);

            tune_days.setText(sp.getString(R.string.key_autotune_default_tune_days,"5"));
            warningView.setText(addWarnings());
            resultView.setText("");

            Date lastRun = autotunePlugin.lastRun!=null? autotunePlugin.lastRun : new Date(0);
            if (lastRun.getTime() > dateUtil.toTimeMinutesFromMidnight(System.currentTimeMillis(), autotunePlugin.autotuneStartHour*60) && autotunePlugin.result != "") {
                tune_days.setText(autotunePlugin.lastNbDays);
                resultView.setText(autotunePlugin.result);
                autotuneCopyLocalButton.setVisibility(View.VISIBLE);
                autotuneProfileSwitchButton.setVisibility(View.VISIBLE);
                warningView.setText(resourceHelper.gs(R.string.autotune_warning_after_run));
            }
            String lastRunTxt = autotunePlugin.lastRun != null ? dateUtil.dateAndTimeString(autotunePlugin.lastRun) : "";
            lastRunView.setText(lastRunTxt);
            updateGUI();
            return view;
        } catch (Exception e) {
        }

        return null;
    }

// disabled by philoul to build AAPS
// @OnClick(R.id.nsprofile_profileswitch)
    public void onClickProfileSwitch() {
        String name = resourceHelper.gs(R.string.autotune_tunedprofile_name);
        ProfileStore store = nsProfilePlugin.getProfile();
        if (store != null) {
            Profile profile = store.getSpecificProfile(name);
            if (profile != null) {
// todo Philoul activate profile switch once AT is Ok (to update to be compatible with local profiles)
//                 OKDialog.showConfirmation(getActivity(), MainApp.gs(R.string.activate_profile) + ": " + name + " ?", () ->
//                         NewNSTreatmentDialog.doProfileSwitch(store, name, 0, 100, 0)
//                 );
            }
        }
    }
    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.autotune_run) {
            //updateResult("Starting Autotune");
            int daysBack = Integer.parseInt(tune_days.getText().toString());
            if (daysBack > 0) {
//            resultView.setText(autotune.bgReadings(daysBack));
                resultView.setText(autotunePlugin.aapsAutotune(daysBack));
                autotuneProfileSwitchButton.setVisibility(View.VISIBLE);
                autotuneCopyLocalButton.setVisibility(View.VISIBLE);
                warningView.setText(resourceHelper.gs(R.string.autotune_warning_after_run));
                String lastRunTxt = AutotunePlugin.lastRun != null ? "" + dateUtil.dateAndTimeString(AutotunePlugin.lastRun) : "";
                lastRunView.setText(lastRunTxt);
            } else
                resultView.setText("Set at least 1 day!!!");
            // lastrun in minutes ???
        } else if (id == R.id.autotune_profileswitch){
            String name = resourceHelper.gs(R.string.autotune_tunedprofile_name);
            ProfileStore profileStore = autotunePlugin.tunedProfile.getProfileStore();
            log("ProfileSwitch pressed");

            if (profileStore != null) {
//                NewNSTreatmentDialog newDialog = new NewNSTreatmentDialog();
//                final OptionsToShow profileswitch = CareportalFragment.PROFILESWITCH;
//                profileswitch.executeProfileSwitch = true;
//                newDialog.setOptions(profileswitch, R.string.careportal_profileswitch);
//                newDialog.show(getFragmentManager(), "NewNSTreatmentDialog");

// todo Philoul activate profile switch once AT is Ok
//                 OKDialog.showConfirmation(getActivity(), MainApp.gs(R.string.activate_profile) + ": " + name + " ?", () ->
//                         NewNSTreatmentDialog.doProfileSwitch(store, name, 0, 100, 0)
//                 );
            } else
                log("ProfileStore is null!");

        }else if (id == R.id.autotune_copylocal){

        }

        //updateGUI();
    }

    // disabled by philoul to build AAPS
    //@Override
    protected void updateGUI() {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {


                }
            });
    }


    //Todo add Strings and format text according to units
    private String addWarnings() {
        String warning = "";
        String nl = "";
        int toMgDl=1;
        if(profileFunction.getUnits().equals("mmol"))
            toMgDl = 18;
        ATProfile profile = new ATProfile(profileFunction.getProfile(System.currentTimeMillis()));
        if(!profile.isValid)
            return "Non profile selected";
        if (profile.getIcSize()>1) {
            warning = nl + "Autotune works with only one IC value, your profile has " + profile.getIcSize() + " values. Average value is " + profile.ic + "g/U";
            nl="\n";
        }
        if (profile.getIsfSize()>1) {
            warning = nl + "Autotune works with only one ISF value, your profile has " + profile.getIsfSize() + " values. Average value is " + profile.isf/toMgDl + profileFunction.getUnits() + "/U";
            nl="\n";
        }

        return warning;
    }

    //update if possible AutotuneFragment at beginning and during calculation between each day
    public void updateResult(String message) {
        resultView.setText(message);
        updateGUI();
    }

    private void log(String message) {
        autotunePlugin.atLog("Fragment] " + message);
    }

}
