/**
 * Copyright (C) 2016 Christopher J. Stehno
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
package com.stehno.ersatz;

import com.stehno.ersatz.model.ResponseImpl;

/**
 * Created by cjstehno on 12/2/16.
 */
public interface Response {

    ResponseImpl body(final Object content);

    ResponseImpl header(final String name, final String value);

    ResponseImpl cookie(final String name, final String value);

    ResponseImpl contentType(final String contentType);

    ResponseImpl code(int code);
}
