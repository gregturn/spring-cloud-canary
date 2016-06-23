/*
 * Copyright 2016 the original author or authors.
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
package org.springframework.cloud.canary;

import java.util.List;

import lombok.Data;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

/**
 * @author Greg Turnquist
 */
@Component
@ConfigurationProperties(prefix = "canary")
@Data
public class CanaryCriteria {

	private List<String> metrics;
	private String limit; // stored as percent string, e.g. 10.4%

	private static float toValue(String percentageString) {
		Assert.state(percentageString.endsWith("%"), "limit must end with '%'");

		return new Float(percentageString.substring(0, percentageString.length()-1)) / 100.0f;
	}

	public boolean withinLimit(String percentString) {

		final float actual = Math.abs(toValue(percentString));
		final float expected = Math.abs(toValue(this.limit));
		return actual < expected;
	}
}
