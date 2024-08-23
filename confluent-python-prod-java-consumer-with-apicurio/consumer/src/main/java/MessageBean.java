/*
 * Copyright 2020 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.StringJoiner;


public record MessageBean(String name, @JsonProperty(value = "favorite_number") int favoriteNumber, @JsonProperty(value = "favorite_color")  String favoriteColor) {

    @Override
    public String toString() {
        return new StringJoiner(", ", MessageBean.class.getSimpleName() + "[", "]")
                .add("name='" + name + "'")
                .add("favoriteNumber=" + favoriteNumber)
                .add("favoriteColor='" + favoriteColor + "'")
                .toString();
    }
}