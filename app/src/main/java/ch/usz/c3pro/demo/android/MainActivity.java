package ch.usz.c3pro.demo.android;

import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
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
import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.FitnessStatusCodes;
import com.google.android.gms.fitness.data.DataType;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.hl7.fhir.dstu3.model.Contract;
import org.hl7.fhir.dstu3.model.Observation;
import org.hl7.fhir.dstu3.model.Quantity;
import org.hl7.fhir.dstu3.model.Questionnaire;
import org.hl7.fhir.dstu3.model.QuestionnaireResponse;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.researchstack.backbone.ResourcePathManager;
import org.researchstack.backbone.result.TaskResult;

import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import ch.usz.c3pro.c3_pro_android_framework.consent.ViewConsentTaskActivity;
import ch.usz.c3pro.c3_pro_android_framework.dataqueue.DataQueue;
import ch.usz.c3pro.c3_pro_android_framework.dataqueue.EncryptedDataQueue;
import ch.usz.c3pro.c3_pro_android_framework.errors.C3PROErrorCode;
import ch.usz.c3pro.c3_pro_android_framework.googlefit.GoogleFitAgent;
import ch.usz.c3pro.c3_pro_android_framework.pyromaniac.Pyro;
import ch.usz.c3pro.c3_pro_android_framework.pyromaniac.async.Callback;
import ch.usz.c3pro.c3_pro_android_framework.pyromaniac.async.ReadJasonQuestionnaireFromURLAsyncTask;
import ch.usz.c3pro.c3_pro_android_framework.pyromaniac.logic.consent.ConsentSummary;
import ch.usz.c3pro.c3_pro_android_framework.pyromaniac.logic.consent.ContractAsTask;
import ch.usz.c3pro.c3_pro_android_framework.questionnaire.QuestionnaireAdapter;
import ch.usz.c3pro.c3_pro_android_framework.questionnaire.ViewQuestionnaireTaskActivity;

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
    //debug
    public static final String LTAG = "LC3P";

    //files
    public static String questionnaireListFilePath = "questionnaire_list";
    public static String contractFilePath = "contract.json";

    private GoogleApiClient googleApiClient = null;

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

        surveyListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                Log.d(LTAG, "about to encrypt...");
                testEncryptResource((Questionnaire) surveyListView.getItemAtPosition(position));
                return true;
            }
        });


        /**
         * Reading all Questionnaires listed in the questionnaire_list in the raw folder from their
         * URLs in a background task and adding them to the listView defined in the layout file once
         * they are loaded.
         * */

        String questionnaireList = ResourcePathManager.getResourceAsString(this, questionnaireListFilePath);
        List<String> urlList = Arrays.asList(questionnaireList.split("[\\r\\n]+"));
        for (final String url : urlList) {
            new ReadJasonQuestionnaireFromURLAsyncTask(url, url, new Callback.QuestionnaireReceiver() {
                @Override
                public void onSuccess(String requestID, Questionnaire result) {
                    questionnaireAdapter.add(result);
                }

                @Override
                public void onFail(String requestID, C3PROErrorCode code) {
                    Log.d(LTAG, "Reading questionnaire from URL failed: " + requestID + " error: " + code.toString());
                }
            }).execute();
        }

        /**
         * The click of the button gets the step count for the past week and shows it in a toast notification.
         * */
        AppCompatButton stepButton = (AppCompatButton) findViewById(R.id.step_button);
        stepButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Date now = new Date();
                Calendar cal = Calendar.getInstance();
                cal.setTime(now);
                cal.add(Calendar.WEEK_OF_YEAR, -1);
                Date startTime = cal.getTime();

                GoogleFitAgent.getAggregateStepCountBetween(startTime, now, "stepCount", new Callback.QuantityReceiver() {
                    @Override
                    public void onSuccess(String requestID, Quantity result) {
                        /**
                         * request ID can be used to identify the request. (Not necessary in this
                         * case! Just for demonstration here.)
                         * */
                        if (requestID.equals("stepCount")) {
                            Toast.makeText(MainActivity.this, result.getValue().intValue() + " " + result.getUnit(), Toast.LENGTH_LONG).show();
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

                GoogleFitAgent.getLatestSampleOfHeight("height", new Callback.QuantityReceiver() {
                    @Override
                    public void onSuccess(String requestID, Quantity result) {
                        Toast.makeText(MainActivity.this, result.getValue().floatValue() + " " + result.getUnit(), Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onFail(String requestID, C3PROErrorCode code) {

                    }
                });
            }
        });

        /**
         * Print Observation of Weight summary over the past year to the TextView declared in
         * the layout xml
         * */
        AppCompatButton summaryButton = (AppCompatButton) findViewById(R.id.summary_button);
        summaryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Date now = new Date();
                Calendar cal = Calendar.getInstance();
                cal.setTime(now);
                cal.add(Calendar.WEEK_OF_YEAR, -52);
                Date startTime = cal.getTime();

                GoogleFitAgent.getWeightSummaryBetween(startTime, now, "weightSummary", new Callback.ObservationReceiver() {
                    @Override
                    public void onSuccess(String requestID, Observation result) {
                        printObservation(result);
                        String weightSummaryString = Pyro.getFhirContext().newJsonParser().encodeResourceToString(result);
                        Toast.makeText(MainActivity.this, weightSummaryString, Toast.LENGTH_LONG);
                    }

                    @Override
                    public void onFail(String requestID, C3PROErrorCode code) {

                    }
                });
            }
        });

        /**
         * Decrypt encrypted Resource in TextView
         * */
        AppCompatButton decryptButton = (AppCompatButton) findViewById(R.id.decrypt_button);
        decryptButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AppCompatTextView resultView = (AppCompatTextView) findViewById(R.id.result_textView);
                String encryptedString = resultView.getText().toString();
                if (!Strings.isNullOrEmpty(encryptedString)) {
                    testDecryptString(encryptedString);
                }
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

        /**
         * Launches consent
         * */
        AppCompatButton consentButton = (AppCompatButton) findViewById(R.id.consent_button);
        consentButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchConsent();
            }
        });
    }

    private void launchConsent() {
        String contractString = ResourcePathManager.getResourceAsString(this, contractFilePath);
        Contract contract = Pyro.getFhirContext().newJsonParser().parseResource(Contract.class, contractString);

        Intent intent = ViewConsentTaskActivity.newIntent(this, contract);
        startActivityForResult(intent, 111);
    }

    private void launchSurvey(Questionnaire questionnaire) {

        Intent intent = ViewQuestionnaireTaskActivity.newIntent(this, questionnaire);

        startActivityForResult(intent, 222);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == AppCompatActivity.RESULT_OK) {

            switch (requestCode) {
                case 111:

                    // TEST: get the eligibility from the stepResult
                    TaskResult result = (TaskResult) data.getSerializableExtra(ViewConsentTaskActivity.EXTRA_TASK_RESULT);
                    boolean eligible = (boolean) result.getStepResult(ContractAsTask.ID_ELIGIBILITY_ASSESSMENT_STEP).getResult();

                    ConsentSummary summary = (ConsentSummary) data.getExtras().get(ViewConsentTaskActivity.EXTRA_CONSENT_SUMMARY);
                    Toast.makeText(MainActivity.this, "Eligibility Status is: " + eligible + "\nConsent Status is: " + summary.hasConsented(), Toast.LENGTH_SHORT).show();
                    break;

                case 222:
                    QuestionnaireResponse response = (QuestionnaireResponse) data.getExtras().get(ViewQuestionnaireTaskActivity.EXTRA_QUESTIONNAIRE_RESPONSE);
                    printQuestionnaireAnswers(response);
                    break;
            }
        } else if (resultCode == AppCompatActivity.RESULT_CANCELED) {

        }
    }

    private void testEncryptResource(IBaseResource resource) {
        try {
            // encrypt
            JsonObject jsonObject = EncryptedDataQueue.getInstance().encryptResource(resource);
            AppCompatTextView resultView = (AppCompatTextView) findViewById(R.id.result_textView);
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String prettyJson = gson.toJson(jsonObject);

            resultView.setText(prettyJson);
            Log.d(LTAG, "Encrypted resource: ");
            Log.d(LTAG, prettyJson);
        } catch (GeneralSecurityException e) {
            e.printStackTrace();
        }
    }

    private void testDecryptString(String encryptedString) {

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        JsonElement jsonElement = gson.fromJson(encryptedString, JsonElement.class);
        JsonObject jsonObject = jsonElement.getAsJsonObject();

        if (jsonObject != null) {
            // decrypt
            IBaseResource decryptedResource = null;
            try {
                decryptedResource = EncryptedDataQueue.getInstance().decryptResource(jsonObject);
                if (decryptedResource != null) {
                    String decryptedResourceString = Pyro.getFhirContext().newJsonParser().encodeResourceToString(decryptedResource);

                    Log.d(LTAG, "Decrypted resource: ");
                    Log.d(LTAG, decryptedResourceString);
                    AppCompatTextView resultView = (AppCompatTextView) findViewById(R.id.result_textView);
                    resultView.setText(decryptedResourceString);
                }
            } catch (GeneralSecurityException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Prints the QuestionnaireResponse into the textView of the main activity under the list of questionnaires.
     */
    private void printQuestionnaireAnswers(QuestionnaireResponse response) {
        String results = Pyro.getFhirContext().newJsonParser().encodeResourceToString(response);
        AppCompatTextView resultView = (AppCompatTextView) findViewById(R.id.result_textView);
        resultView.setText(results);
    }

    /**
     * Prints an Observation into the textView of the main activity under the list of questionnaires.
     */
    private void printObservation(Observation observation) {
        String results = Pyro.getFhirContext().newJsonParser().encodeResourceToString(observation);
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

