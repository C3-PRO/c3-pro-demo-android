package ch.usz.c3pro.demo.android;

import android.os.AsyncTask;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.DataSource;
import com.google.android.gms.fitness.data.DataType;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * C3PRO
 * <p/>
 * Created by manny Weber on 07/05/2016.
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
 * Run this task to create sample data if there is no height or weight data on the test device.
 * The GoogleapiClient needs to be configured and the READ_WRITE permissions for body data requested.
 * */
public class CreateFitDataTask extends AsyncTask<GoogleApiClient, Void, Void> {
public static String LTAG = "LC3P";
    @Override
    protected Void doInBackground(GoogleApiClient... params) {
        /**
         * create a height and weight entry to be read later
         * */
        DataSet heightDataSet = createHeightForRequest();
        Log.d(LTAG, "about to write height");
        com.google.android.gms.common.api.Status weightInsertStatus1 =
                Fitness.HistoryApi.insertData(params[0], heightDataSet)
                        .await(1, TimeUnit.MINUTES);
        Log.d(LTAG, "done");

        DataSet weightDataSet = createWeightForRequest();
        Log.d(LTAG, "about to write height");
        com.google.android.gms.common.api.Status weightInsertStatus2 =
                Fitness.HistoryApi.insertData(params[0], weightDataSet)
                        .await(1, TimeUnit.MINUTES);
        Log.d(LTAG, "done");

        return null;
    }

    private DataSet createHeightForRequest() {
        // create height entry
        DataSource dataSource = new DataSource.Builder()
                .setAppPackageName("ch.usz.c3pro.demo.android")
                .setDataType(DataType.TYPE_HEIGHT)
                .setType(DataSource.TYPE_RAW)
                .build();

        DataSet dataSet = DataSet.create(dataSource);
        DataPoint dataPoint = dataSet.createDataPoint().setTimeInterval(new Date().getTime(), new Date().getTime(), TimeUnit.MILLISECONDS);
        dataPoint = dataPoint.setFloatValues(new Float(1.68));

        dataSet.add(dataPoint);

        return dataSet;
    }

    private DataSet createWeightForRequest() {
        // create height entry
        DataSource dataSource = new DataSource.Builder()
                .setAppPackageName("ch.usz.c3pro.demo.android")
                .setDataType(DataType.TYPE_WEIGHT)
                .setType(DataSource.TYPE_RAW)
                .build();

        DataSet dataSet = DataSet.create(dataSource);
        DataPoint dataPoint = dataSet.createDataPoint().setTimeInterval(new Date().getTime(), new Date().getTime(), TimeUnit.MILLISECONDS);
        dataPoint = dataPoint.setFloatValues(new Float(65));
        dataSet.add(dataPoint);

        return dataSet;
    }
}
