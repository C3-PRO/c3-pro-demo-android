package ch.usz.c3pro.demo.android;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.annotation.StyleRes;
import android.support.design.widget.Snackbar;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatButton;
import android.support.v7.widget.AppCompatTextView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.FitnessStatusCodes;
import com.google.android.gms.fitness.data.DataType;

import org.hl7.fhir.dstu3.model.Observation;
import org.hl7.fhir.dstu3.model.Quantity;
import org.hl7.fhir.dstu3.model.Questionnaire;
import org.hl7.fhir.dstu3.model.QuestionnaireResponse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import ch.usz.c3pro.c3_pro_android_framework.C3PRO;
import ch.usz.c3pro.c3_pro_android_framework.C3PROErrorCode;
import ch.usz.c3pro.c3_pro_android_framework.dataqueue.DataQueue;
import ch.usz.c3pro.c3_pro_android_framework.dataqueue.jobs.LoadResultJob;
import ch.usz.c3pro.c3_pro_android_framework.dataqueue.jobs.ReadQuestionnaireFromURLJob;
import ch.usz.c3pro.c3_pro_android_framework.googlefit.GoogleFitAgent;
import ch.usz.c3pro.c3_pro_android_framework.questionnaire.QuestionnaireAdapter;
import ch.usz.c3pro.c3_pro_android_framework.questionnaire.QuestionnaireFragment;

/**
 * C3PRO
 * <p/>
 * Created by manny Weber on 04/19/16.
 * Copyright © 2016 University Hospital Zurich. All rights reserved.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * This is the main activity of the sample application to show how you can use C3PRO to conduct
 * a survey you have available as a HAPI FHIR Questionnaire
 */
public class MainActivity extends AppCompatActivity {
    private GoogleApiClient googleApiClient = null;
    public static final String LTAG = "LC3P";

    @Override
    public MenuInflater getMenuInflater() {
        return super.getMenuInflater();
    }

    public MainActivity() {
        super();
    }

    @Override
    public void setTheme(@StyleRes int resid) {
        super.setTheme(resid);
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public ActionBar getSupportActionBar() {
        return super.getSupportActionBar();
    }

    @Override
    public void setSupportActionBar(@Nullable Toolbar toolbar) {
        super.setSupportActionBar(toolbar);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        /**
         * Set the app up to collect Google Fit data.
         * See the method implementation for more details about how to ask the user for permissions
         * and subscribe to i.e. step count data
         * */
        buildFitnessClient();

        /**
         * Initialize the GoogleFitAgent. It is then ready to provide step count, hight and weight
         * data directly as FHIR Quantities and Observations.
         * */
        GoogleFitAgent.init(googleApiClient);

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
                launchSurvey((Questionnaire) surveyListView.getItemAtPosition(position));
            }
        });

        /**
         * Reading all Questionnaires listed in the questionnaire_list in the raw folder from their
         * URLs in a background task and adding them to the listView defined in the layout file once
         * they are loaded.
         * */
        String questionnaireList = C3PRO.getDataQueue().getRawFileAsString(getResources(), R.raw.questionnaire_list);
        List<String> urlList = Arrays.asList(questionnaireList.split("[\\r\\n]+"));
        for (final String url : urlList) {
            C3PRO.getDataQueue().getJsonQuestionnaireFromURL(url, url, new DataQueue.CreateQuestionnaireCallback() {
                @Override
                public void onSuccess(String requestID, Questionnaire result) {
                    questionnaireAdapter.add(result);
                }

                @Override
                public void onFail(String requestID, C3PROErrorCode code) {
                    Log.d(LTAG, "Reading questionnaire from URL failed: " + requestID + " error: " + code.toString());
                }
            });
        }

        /**
         * The click of the button gets the step count for the past two weeks and shows it in a toast notification.
         * */
        AppCompatButton stepButton = (AppCompatButton) findViewById(R.id.step_button);
        stepButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Date now = new Date();
                Calendar cal = Calendar.getInstance();
                cal.setTime(now);
                cal.add(Calendar.WEEK_OF_YEAR, -2);
                Date startTime = cal.getTime();

                GoogleFitAgent.getAggregateStepCountBetween(startTime, now, "stepCount", new GoogleFitAgent.QuantityReceiver() {
                    @Override
                    public void onSuccess(String requestID, Quantity result) {
                        /**
                         * request ID can be used to identify the request. (Not necessary in this
                         * case! Just for demonstration here.)
                         * */
                        if (requestID.equals("stepCount")) {
                            Toast.makeText(MainActivity.this, result.getValue().intValue() + " " + result.getUnit(), Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFail(String requestID, C3PROErrorCode code) {

                    }
                });
            }
        });

        /**
         * The click of the button gets the user's height and shows it in a toast notification.
         * */
        AppCompatButton heightButton = (AppCompatButton) findViewById(R.id.height_button);
        heightButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                GoogleFitAgent.getLatestSampleOfHeight("height", new GoogleFitAgent.QuantityReceiver() {
                    @Override
                    public void onSuccess(String requestID, Quantity result) {
                        Toast.makeText(MainActivity.this, result.getValue().floatValue() + " " + result.getUnit(), Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onFail(String requestID, C3PROErrorCode code) {

                    }
                });
            }
        });

        /**
         * Print Observation of Weight summary over the past two weeks to the TextView declared in
         * the layout xml
         * */
        AppCompatButton summaryButton = (AppCompatButton) findViewById(R.id.summary_button);
        summaryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Date now = new Date();
                Calendar cal = Calendar.getInstance();
                cal.setTime(now);
                cal.add(Calendar.WEEK_OF_YEAR, -2);
                Date startTime = cal.getTime();

                GoogleFitAgent.getWeightSummaryBetween(startTime, now, "weightSummary", new GoogleFitAgent.ObservationReceiver() {
                    @Override
                    public void onSuccess(String requestID, Observation result) {
                        printObservation(result);
                    }

                    @Override
                    public void onFail(String requestID, C3PROErrorCode code) {

                    }
                });
            }
        });

        /**
         * Clears the TextView declared in the layout xml
         * */
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
             * If the fragment has been added before, the TaskViewActivity can be started directly,
             * assuming that it was prepared right after the fragment was created.
             * */
            fragment.startTaskViewActivity();
        } else {
            /**
             * If the fragment does not exist, create it, add it to the fragment manager and
             * let it prepare the TaskViewActivity
             * */
            final QuestionnaireFragment questionnaireFragment = new QuestionnaireFragment();
            questionnaireFragment.newInstance(questionnaire, new QuestionnaireFragment.QuestionnaireFragmentListener() {
                @Override
                public void whenTaskReady(String requestID) {
                    /**
                     * Only when the task is ready, the survey is started
                     * */
                    questionnaireFragment.startTaskViewActivity();
                }

                @Override
                public void whenCompleted(String requestID, QuestionnaireResponse questionnaireResponse) {
                    /**
                     * Where the response for a completed survey is received. Here it is printed
                     * to a TextView defined in the app layout.
                     * */
                    printQuestionnaireAnswers(questionnaireResponse);
                }

                @Override
                public void whenCancelledOrFailed(C3PROErrorCode code) {
                    /**
                     * If the task can not be prepared, a backup plan is needed.
                     * Here the fragment is removed from the FragmentManager so it can be created
                     * again later
                     * */
                    if (code == C3PROErrorCode.RESULT_CANCELLED) {
                        /**
                         * user just cancelled activity. do nothing
                         * */
                    } else {
                        /**
                         * If the task can not be prepared, a backup plan is needed.
                         * Here the fragment is removed from the FragmentManager so it can be created
                         * again later.
                         * */
                        Log.e(LTAG, code.toString());
                        getSupportFragmentManager().beginTransaction().remove(questionnaireFragment).commit();
                    }
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
     * Prints an Observation into the textView of the main activity under the list of questionnaires.
     */
    private void printObservation(Observation observation) {
        String results = C3PRO.getFhirContext().newJsonParser().encodeResourceToString(observation);
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
     * Build a {@link GoogleApiClient} that will authenticate the user and allow the application
     * to connect to Fitness APIs. The scopes included should match the scopes your app needs
     * (see documentation for details). Authentication will occasionally fail intentionally,
     * and in those cases, there will be a known resolution, which the OnConnectionFailedListener()
     * can address. Examples of this include the user never having signed in before, or having
     * multiple accounts on the device and needing to specify which account to use, etc.
     */
    private void buildFitnessClient() {
        //TODO: Create the Google API Client
        /**
         * In order to access Google Fit data and write data to Google Fit history, the APIs and
         * scopes have to be defined here.
         * Only APIs that are going to be used should be added here. Read only scopes can be used if
         * no data has to be written back to the Google Fit history. Permissions have to be asked for
         * and declared in the AndroidManifest.xml
         * */
        googleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Fitness.RECORDING_API)
                .addApi(Fitness.HISTORY_API)
                .addApi(Fitness.SENSORS_API)
                .addScope(new Scope(Scopes.FITNESS_ACTIVITY_READ_WRITE))
                .addScope(new Scope(Scopes.FITNESS_BODY_READ_WRITE))
                .addConnectionCallbacks(
                        new GoogleApiClient.ConnectionCallbacks() {

                            @Override
                            public void onConnected(Bundle bundle) {
                                Log.i(LTAG, "Connected!!!");
                                /**
                                 * The connection is established, calls to the Fitness APIs can now
                                 * be made. See subscribe() function to see how to subscribe to i.e.
                                 * step count data.
                                 * */
                                //TODO: Subscribe to some data sources!
                                subscribe();

                                /**
                                 * If no fit data is available on the test phone, create height and
                                 * weight entries for testing
                                 * */
                                // GoogleFitAgent.enterHeightDataPoint(getApplicationContext(), 1.68f);
                                // GoogleFitAgent.enterWeightDataPoint(getApplicationContext(), 64f);
                            }

                            @Override
                            public void onConnectionSuspended(int i) {
                                /**
                                 * Decide what to do if the connection to the Fitness sensors is lost
                                 * at some point. The cause can be read from the ConnectionCallbacks
                                 * */
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
        /**
         * To create a subscription, invoke the Recording API. As soon as the subscription is
         * active, fitness data will start recording.
         * The recording API has to have been added while building the FitnessClient!
         */
        Fitness.RecordingApi.subscribe(googleApiClient, DataType.TYPE_STEP_COUNT_DELTA)
                .setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status status) {
                        if (status.isSuccess()) {
                            if (status.getStatusCode()
                                    == FitnessStatusCodes.SUCCESS_ALREADY_SUBSCRIBED) {
                                Log.i(LTAG, "Step Count: Existing subscription for activity detected.");
                            } else {
                                Log.i(LTAG, "Step Count: Successfully subscribed!");
                            }
                        } else {
                            Log.i(LTAG, "Step Count: There was a problem subscribing.");
                        }
                    }
                });

        Fitness.RecordingApi.subscribe(googleApiClient, DataType.TYPE_HEIGHT)
                .setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status status) {
                        if (status.isSuccess()) {
                            if (status.getStatusCode()
                                    == FitnessStatusCodes.SUCCESS_ALREADY_SUBSCRIBED) {
                                Log.i(LTAG, "Height: Existing subscription for activity detected.");
                            } else {
                                Log.i(LTAG, "Height: Successfully subscribed!");
                            }
                        } else {
                            Log.i(LTAG, "Height: There was a problem subscribing.");
                        }
                    }
                });

        Fitness.RecordingApi.subscribe(googleApiClient, DataType.TYPE_WEIGHT)
                .setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status status) {
                        if (status.isSuccess()) {
                            if (status.getStatusCode()
                                    == FitnessStatusCodes.SUCCESS_ALREADY_SUBSCRIBED) {
                                Log.i(LTAG, "Weight: Existing subscription for activity detected.");
                            } else {
                                Log.i(LTAG, "Weight: Successfully subscribed!");
                            }
                        } else {
                            Log.i(LTAG, "Weight: There was a problem subscribing.");
                        }
                    }
                });
    }
}

