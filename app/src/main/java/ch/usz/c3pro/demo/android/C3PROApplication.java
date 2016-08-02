package ch.usz.c3pro.demo.android;

import android.app.Application;
import android.content.Context;
import android.support.multidex.MultiDex;

import org.researchstack.backbone.StorageAccess;
import org.researchstack.backbone.storage.database.AppDatabase;
import org.researchstack.backbone.storage.database.sqlite.DatabaseHelper;
import org.researchstack.backbone.storage.file.EncryptionProvider;
import org.researchstack.backbone.storage.file.FileAccess;
import org.researchstack.backbone.storage.file.PinCodeConfig;
import org.researchstack.backbone.storage.file.SimpleFileAccess;
import org.researchstack.backbone.storage.file.UnencryptedProvider;

import ch.usz.c3pro.c3_pro_android_framework.dataqueue.DataQueue;

/**
 * C3PRO
 *
 * Created by manny Weber on 04/28/16.
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
 * This is a sample application to show how to set up your app for using ResearchStack and FHIR.
 * The HAPI library is not yet optimized for use in android. It makes sense to keep the FHIR Context
 * around as a singleton, which the C3PRO class will do, if initialized with the app context. This
 * helps keeping the use of resources minimal.
 * ResearchStack configurations are copied from the ResearchStack sample app.
 */
public class C3PROApplication extends Application {

    // TODO: make it a multidex application
    /* with HAPI it seemed necessary to enable multidex to accomodate the large
    number of operations provided by the package. It seems to work without it now. Leaving it here
    for now just in case.*/
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // TODO: initialize c3pro
        /**
         * Initialize DataQueue:
         * You have to provide a context (your application) and an URL to the FHIR Server.
         * Once initialized, DataQueue can write and read Resources from your server in a
         * background thread.
         * */
        DataQueue.init(this, "http://fhirtest.uhn.ca/baseDstu3");

        // TODO: following are some ResearchStack settings. For more info, visit http://researchstack.org
        // ResearchStack: Customize your pin code preferences
        PinCodeConfig pinCodeConfig = new PinCodeConfig(); // default pin config (4-digit, 1 min lockout)

        // ResearchStack: Customize encryption preferences
        EncryptionProvider encryptionProvider = new UnencryptedProvider(); // No pin, no encryption

        // ResearchStack: If you have special file handling needs, implement FileAccess
        FileAccess fileAccess = new SimpleFileAccess();

        // ResearchStack: If you have your own custom database, implement AppDatabase
        AppDatabase database = new DatabaseHelper(this,
                DatabaseHelper.DEFAULT_NAME,
                null,
                DatabaseHelper.DEFAULT_VERSION);

        StorageAccess.getInstance().init(pinCodeConfig, encryptionProvider, fileAccess, database);
    }
}
