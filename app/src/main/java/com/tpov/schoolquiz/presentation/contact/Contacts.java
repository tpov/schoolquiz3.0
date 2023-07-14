/*
 * Copyright 2016 Tamir Shomer
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

package com.tpov.schoolquiz.presentation.contact;

import android.content.Context;

public final class Contacts {
    private static Context context;

    private Contacts() {}

    /**
     * Initialize the Contacts library
     *
     * @param context context
     */
    public static void initialize(Context context) {
        Contacts.context = context.getApplicationContext();
    }

    /**
     * Get a new Query object to find contacts.
     *
     * @return  A new Query object.
     */
    public static Query getQuery() {
        if (Contacts.context == null) {
            throw new IllegalStateException("Contacts library not initialized");
        }

        return new Query(context);
    }
}
