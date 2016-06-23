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

import static java.util.stream.Collectors.groupingBy;

import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Value;

/**
 * @author Greg Turnquist
 */
@Value
@Builder
public class CompositeAnalysis implements Analysis {

	private List<SimpleAnalysis> analysisList;

	@Override
	public Status getStatus() {

		Map<Status, List<SimpleAnalysis>> groupings = this.analysisList.stream().collect(groupingBy(SimpleAnalysis::getStatus));

		if (groupings.containsKey(Status.INVALID)) {
			return Status.INVALID;
		} else if (groupings.containsKey(Status.DEGRADED)) {
			return Status.DEGRADED;
		} else {
			return Status.UP;
		}
	}
}
