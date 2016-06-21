package ch.usz.c3pro.demo.android;

import android.content.Context;
import android.content.res.Resources;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import org.hl7.fhir.dstu3.model.Questionnaire;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.lang.reflect.Field;
import java.util.ArrayList;

import ca.uhn.fhir.parser.IParser;
import ch.usz.c3pro.c3_pro_android_framework.C3PRO;

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
 * This class can provide sampledata to be used with C3PRO. This is only for demonstration
 * purposes. Use your own resources for your apps.
 */
public class SampleData extends AppCompatActivity {

    /**
     * get the all the files in the raw resource folder starting with "questionnaire" as FHIR Questionnaires
     */
    public static ArrayList<Questionnaire> getAllRawQuestionnaires(Context context){
        ArrayList<Questionnaire> questionnaires = new ArrayList<Questionnaire>();
        String[] rawNames = getAllRawQuestionnaireResources();
        for (int i = 0; i<rawNames.length; i++){

            int rawID = context.getResources().getIdentifier(rawNames[i],
                    "raw", context.getPackageName());

            Questionnaire questionnaire = SampleData.getQuestionnaireFromJson(context.getResources(), rawID);
            questionnaires.add(questionnaire);
        }
        return questionnaires;
    }

    /**
     * returns a Questionnaire from the jason with corresponding to the rawID from the "raw" resource
     * folder.
     * */
    public static Questionnaire getQuestionnaireFromJson(Resources res, int rawID) {

        IParser parser = C3PRO.getFhirContext().newJsonParser();

        String json = getJasonAsString(res, rawID);

        return parser.parseResource(Questionnaire.class, json);

    }

    /**
     * loads the content of the file corresponding to the rawID into a string.
     * */
    public static String getJasonAsString(Resources res, int rawID) {

        //InputStream is = res.openRawResource(R.raw.questionnaire_textvalues);
        InputStream is = res.openRawResource(rawID);
        Writer writer = new StringWriter();
        char[] buffer = new char[1024];
        try {
            Reader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            int n;
            while ((n = reader.read(buffer)) != -1) {
                writer.write(buffer, 0, n);
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return writer.toString();
    }

    /**
     * get the names of all the files in the raw resource folder starting with "questionnaire"
     */
    public static String[] getAllRawQuestionnaireResources() {
        Field fields[] = R.raw.class.getDeclaredFields();
        ArrayList<String> nameList = new ArrayList();

        try {
            for (int i = 0; i < fields.length; i++) {
                Field f = fields[i];
                if (f.getName().startsWith("questionnaire")) {
                    nameList.add(f.getName());
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        String[] names = nameList.toArray(new String[nameList.size()]);
        return names;
    }

    public static String readFile(String path) {
        File file = new File(path);
        String content = "";
        try {

            content = getFileContents(file);
        } catch (IOException e) {
            e.printStackTrace();
        }

        Log.d("readFile", content);
        return content;
    }

    public static String getFileContents(final File file) throws IOException {
        final InputStream inputStream = new FileInputStream(file);
        final BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

        final StringBuilder stringBuilder = new StringBuilder();

        boolean done = false;

        while (!done) {
            final String line = reader.readLine();
            done = (line == null);

            if (line != null) {
                stringBuilder.append(line);
            }
        }

        reader.close();
        inputStream.close();

        return stringBuilder.toString();
    }
}
