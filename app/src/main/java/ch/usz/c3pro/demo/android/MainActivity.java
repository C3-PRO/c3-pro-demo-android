package ch.usz.c3pro.demo.android;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatButton;
import android.support.v7.widget.AppCompatTextView;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import org.hl7.fhir.dstu3.model.Questionnaire;
import org.hl7.fhir.dstu3.model.QuestionnaireResponse;

import ch.usz.c3pro.c3_pro_android_framework.C3PRO;
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
    public static final String LTAG = "FSTK";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /**
         * Adding an Item to the ListView for every questionnaire file in the raw resource folder
         * this is an ugly way to provide sample data. replace with your own data!
         * */
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, SampleData.getAllRawQuestionnaireResources());
        final ListView surveyListView = (ListView) findViewById(R.id.survey_list);
        surveyListView.setAdapter(adapter);

        surveyListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                int rawID = getResources().getIdentifier((String) surveyListView.getItemAtPosition(position),
                        "raw", getPackageName());
                Questionnaire questionnaire = SampleData.getQuestionnaireFromJson(getResources(), rawID);
                launchSurvey(questionnaire);
            }
        });


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
}

