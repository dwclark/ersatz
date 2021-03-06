/*
 * Copyright (C) 2017 Christopher J. Stehno
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.stehno.ersatz.auth

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.transform.Memoized
import io.undertow.security.idm.Account

import java.security.Principal

/**
 * Simple implementation of the <code>Account</code> interface used for BASIC and DIGEST authentication testing.
 */
@CompileStatic @Canonical
class SimpleAccount implements Account {

    final String user
    final Set<String> roles = ['TESTER'] as Set<String>

    @Memoized(protectedCacheSize = 1, maxCacheSize = 1)
    Principal getPrincipal() {
        new Principal() {
            @Override String getName() { user }
        }
    }
}
