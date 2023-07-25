/*
 * Copyright 2017-2022 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
 * Object Storage Classes related to AWS S3.
 * @see <a href="https://aws.amazon.com/s3/">AWS S3</a>
 * @author Sergio del Amo
 * @since 1.0.0
 */
@Configuration
@Requires(property = AwsS3Configuration.PREFIX + ".enabled", notEquals = StringUtils.FALSE)
package io.micronaut.objectstorage.aws;

import io.micronaut.context.annotation.Configuration;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
