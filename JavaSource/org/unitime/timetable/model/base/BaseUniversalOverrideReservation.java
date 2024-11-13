/*
 * Licensed to The Apereo Foundation under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 *
 * The Apereo Foundation licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
*/
package org.unitime.timetable.model.base;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

import java.io.Serializable;

import org.unitime.timetable.model.Reservation;
import org.unitime.timetable.model.UniversalOverrideReservation;

/**
 * Do not change this class. It has been automatically generated using ant create-model.
 * @see org.unitime.commons.ant.CreateBaseModelFromXml
 */
@MappedSuperclass
public abstract class BaseUniversalOverrideReservation extends Reservation implements Serializable {
	private static final long serialVersionUID = 1L;

	private String iFilter;
	private Integer iFlags;


	public BaseUniversalOverrideReservation() {
	}

	public BaseUniversalOverrideReservation(Long uniqueId) {
		setUniqueId(uniqueId);
	}


	@Column(name = "filter", nullable = true, length = 512)
	public String getFilter() { return iFilter; }
	public void setFilter(String filter) { iFilter = filter; }

	@Column(name = "override_type", nullable = false)
	public Integer getFlags() { return iFlags; }
	public void setFlags(Integer flags) { iFlags = flags; }

	@Override
	public boolean equals(Object o) {
		if (o == null || !(o instanceof UniversalOverrideReservation)) return false;
		if (getUniqueId() == null || ((UniversalOverrideReservation)o).getUniqueId() == null) return false;
		return getUniqueId().equals(((UniversalOverrideReservation)o).getUniqueId());
	}

	@Override
	public int hashCode() {
		if (getUniqueId() == null) return super.hashCode();
		return getUniqueId().hashCode();
	}

	@Override
	public String toString() {
		return "UniversalOverrideReservation["+getUniqueId()+"]";
	}

	public String toDebugString() {
		return "UniversalOverrideReservation[" +
			"\n	ExpirationDate: " + getExpirationDate() +
			"\n	Filter: " + getFilter() +
			"\n	Flags: " + getFlags() +
			"\n	Inclusive: " + getInclusive() +
			"\n	InstructionalOffering: " + getInstructionalOffering() +
			"\n	Limit: " + getLimit() +
			"\n	StartDate: " + getStartDate() +
			"\n	UniqueId: " + getUniqueId() +
			"]";
	}
}
