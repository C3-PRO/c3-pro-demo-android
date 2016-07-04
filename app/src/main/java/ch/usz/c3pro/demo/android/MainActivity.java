package ch.usz.c3pro.demo.android;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatButton;
import android.support.v7.widget.AppCompatTextView;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.FitnessStatusCodes;
import com.google.android.gms.fitness.data.DataType;

import org.hl7.fhir.dstu3.model.Questionnaire;
import org.hl7.fhir.dstu3.model.QuestionnaireResponse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import ch.usz.c3pro.c3_pro_android_framework.C3PRO;
import ch.usz.c3pro.c3_pro_android_framework.dataqueue.DataQueue;
import ch.usz.c3pro.c3_pro_android_framework.dataqueue.jobs.ReadQuestionnaireFromURLJob;
import ch.usz.c3pro.c3_pro_android_framework.questionnaire.QuestionnaireAdapter;
import ch.usz.c3pro.c3_pro_android_framework.questionnaire.QuestionnaireFragment;

/**
 * C3PRO
 *
 * Created by manny Weber on 04/19/16.
 * Copyright © 2016 University Hospital Zurich. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * This is the main activity of the sample application to show how you can use FHIRSTACK to conduct
 * a survey you have as a HAPI FHIR Questionnaire
 */
public class MainActivity extends AppCompatActivity {
    private GoogleApiClient googleApiClient = null;
    public static final String LTAG = "C3P";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        /**
         * Set the app up to collect step count data
         * */
        buildFitnessClient();

        /**
         * Using the C3PRO QuestionnaireAdapter to display a List of Questionnaires in the ListView
         * defined in the layout file activity_main.xml.
         * When the user clicks one of the Questionnaires, the survey is started.
         * */
        final ArrayAdapter<Questionnaire> questionnaireAdapter = new QuestionnaireAdapter(this, android.R.layout.simple_list_item_1, new ArrayList<Questionnaire>());
        final ListView surveyListView = (ListView) findViewById(R.id.survey_list);
        surveyListView.setAdapter(questionnaireAdapter);

        surveyListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                launchSurvey((Questionnaire)surveyListView.getItemAtPosition(position));
            }
        });

        /**
         * Reading all Questionnaires listed in the questionnaire_list in the raw folder from their
         * URLs in a background task and adding them to the listView defined in the layout file once
         * they are loaded.
         * */
        String questionnaireList = C3PRO.getDataQueue().getRawFileAsString(getResources(), R.raw.questionnaire_list);
        List<String> urlList = Arrays.asList(questionnaireList.split("[\\r\\n]+"));
        for(String url: urlList){
            ReadQuestionnaireFromURLJob qJob = new ReadQuestionnaireFromURLJob(url, url, new DataQueue.QuestionnaireReceiver() {
                @Override
                public void receiveQuestionnaire(String requestID, Questionnaire questionnaire) {
                    questionnaireAdapter.add(questionnaire);
                }
            });
            C3PRO.getDataQueue().addJob(qJob);
        }


        AppCompatButton clearButton = (AppCompatButton) findViewById(R.id.clear_button);
        clearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearData();
                Toast.makeText(MainActivity.this, "cleared!", Toast.LENGTH_SHORT).show();
            }
        });
    }


    /**
     * Launches a survey from a FHIR questionnaire. This is how to use the QuestionnaireFragment
     * Always prepare TaskViewActivity before starting it! It will happen in the background and call
     * back its listener when it's ready.
     * The fragment needs a context in order to run the TaskViewActivity, that's why it has to be
     * committed to the FragmentManager
     */
    private void launchSurvey(Questionnaire questionnaire) {
        /**
         * Looking up if a fragment for the given questionnaire has been created earlier. if so,
         * the survey is started, assuming that the TaskViewActivity has been created before!!
         * The questionnaire IDs are used for identification, assuming they are unique.
         * */
        QuestionnaireFragment fragment = (QuestionnaireFragment) getSupportFragmentManager().findFragmentByTag(questionnaire.getId());
        if (fragment != null) {
            /**
             * If the fragment has been added before, the TaskViewActivity is started
             * */
            fragment.startTaskViewActivity();
        } else {
            /**
             * If the fragment does not exist, we create it, add it to the fragment manager and
             * let it prepare the TaskViewActivity
             * */
            final QuestionnaireFragment questionnaireFragment = new QuestionnaireFragment();
            questionnaireFragment.newInstance(questionnaire, new QuestionnaireFragment.QuestionnaireFragmentListener() {
                @Override
                public void whenTaskReady() {
                    /**
                     * Only when the task is ready, the survey is started
                     * */
                    questionnaireFragment.startTaskViewActivity();
                }

                @Override
                public void whenCompleted(QuestionnaireResponse questionnaireResponse) {
                    /**
                     * Where the response for a completed survey is received. Here it is printed
                     * to a TextView defined in the app layout.
                     * */
                    printQuestionnaireAnswers(questionnaireResponse);
                }

                @Override
                public void whenCancelledOrFailed() {
                    /**
                     * If the task can not be prepared, a backup plan is needed.
                     * Here the fragment is removed from the FragmentManager so it can be created
                     * again later
                     * TODO: proper error handling not yet implemented
                     * */
                    getSupportFragmentManager().beginTransaction().remove(questionnaireFragment).commit();
                }
            });

            /**
             * In order for the fragment to get the context and be found later on, it has to be added
             * to the fragment manager.
             * */
            getSupportFragmentManager().beginTransaction().add(questionnaireFragment, questionnaire.getId()).commit();
            /**
             * prepare the TaskViewActivity. As defined above, it will start the survey once the
             * TaskViewActivity is ready.
             * */
            questionnaireFragment.prepareTaskViewActivity();
        }
    }

    /**
     * Prints the QuestionnaireResponse into the textView of the main activity under the list of questionnaires.
     */
    private void printQuestionnaireAnswers(QuestionnaireResponse response) {
        String results = C3PRO.getFhirContext().newJsonParser().encodeResourceToString(response);
        AppCompatTextView resultView = (AppCompatTextView) findViewById(R.id.result_textView);
        resultView.setText(results);
    }

    /**
     * Clears the data in the TextView defined in the app layout.
     */
    private void clearData() {
        AppCompatTextView resultView = (AppCompatTextView) findViewById(R.id.result_textView);
        resultView.setText("");
    }

    /**
     *  Build a {@link GoogleApiClient} that will authenticate the user and allow the application
     *  to connect to Fitness APIs. The scopes included should match the scopes your app needs
     *  (see documentation for details). Authentication will occasionally fail intentionally,
     *  and in those cases, there will be a known resolution, which the OnConnectionFailedListener()
     *  can address. Examples of this include the user never having signed in before, or having
     *  multiple accounts on the device and needing to specify which account to use, etc.
     */
    private void buildFitnessClient() {
        // Create the Google API Client
        googleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Fitness.RECORDING_API)
                .addScope(new Scope(Scopes.FITNESS_ACTIVITY_READ))
                .addConnectionCallbacks(
                        new GoogleApiClient.ConnectionCallbacks() {

                            @Override
                            public void onConnected(Bundle bundle) {
                                Log.i(LTAG, "Connected!!!");
                                // Now you can make calls to the Fitness APIs.  What to do?
                                // Subscribe to some data sources!
                                subscribe();
                            }

                            @Override
                            public void onConnectionSuspended(int i) {
                                // If your connection to the sensor gets lost at some point,
                                // you'll be able to determine the reason and react to it here.
                                if (i == GoogleApiClient.ConnectionCallbacks.CAUSE_NETWORK_LOST) {
                                    Log.i(LTAG, "Connection lost.  Cause: Network Lost.");
                                } else if (i == GoogleApiClient.ConnectionCallbacks.CAUSE_SERVICE_DISCONNECTED) {
                                    Log.i(LTAG, "Connection lost.  Reason: Service Disconnected");
                                }
                            }
                        }
                )
                .enableAutoManage(this, 0, new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(ConnectionResult result) {
                        Log.i(LTAG, "Google Play services connection failed. Cause: " +
                                result.toString());
                        Snackbar.make(
                                MainActivity.this.findViewById(android.R.id.content),
                                "Exception while connecting to Google Play services: " +
                                        result.getErrorMessage(),
                                Snackbar.LENGTH_INDEFINITE).show();
                    }
                })
                .build();
    }

    public void subscribe() {
        // To create a subscription, invoke the Recording API. As soon as the subscription is
        // active, fitness data will start recording.
        Fitness.RecordingApi.subscribe(googleApiClient, DataType.TYPE_STEP_COUNT_DELTA)
                .setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status status) {
                        if (status.isSuccess()) {
                            if (status.getStatusCode()
                                    == FitnessStatusCodes.SUCCESS_ALREADY_SUBSCRIBED) {
                                Log.i(LTAG, "Existing subscription for activity detected.");
                            } else {
                                Log.i(LTAG, "Successfully subscribed!");
                            }
                        } else {
                            Log.i(LTAG, "There was a problem subscribing.");
                        }
                    }
                });
    }
}

