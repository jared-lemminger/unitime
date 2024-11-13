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
package org.unitime.timetable.model;


import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.cpsolver.ifs.util.ToolBox;
import org.unitime.timetable.model.base.BaseBuildingPref;


/**
 * @author Tomas Muller
 */
@Entity
@Table(name = "building_pref")
public class BuildingPref extends BaseBuildingPref {
	private static final long serialVersionUID = 1L;

/*[CONSTRUCTOR MARKER BEGIN]*/
	public BuildingPref () {
		super();
	}

	/**
	 * Constructor for primary key
	 */
	public BuildingPref (java.lang.Long uniqueId) {
		super(uniqueId);
	}

/*[CONSTRUCTOR MARKER END]*/

    public String preferenceText() {
    	String ret = getBuilding().getAbbreviation();
        if (getRoomIndex() != null)
        	ret += " (" + MSG.itemOnlyRoom(1 + getRoomIndex()) + ")";
    	return ret;
    }
    
    public int compareTo(Object o) {
    	try {
    		BuildingPref p = (BuildingPref)o;
    		int cmp = Integer.compare(getRoomIndex() == null ? -1 : getRoomIndex(), p.getRoomIndex() == null ? -1 : p.getRoomIndex());
    		if (cmp != 0) return cmp;
    		cmp = getBuilding().getAbbreviation().compareTo(p.getBuilding().getAbbreviation());
    		if (cmp!=0) return cmp;
    	} catch (Exception e) {}
    	
    	return super.compareTo(o);
    }
    
    public Object clone() {
    	BuildingPref pref = new BuildingPref();
    	pref.setPrefLevel(getPrefLevel());
    	pref.setBuilding(getBuilding());
    	pref.setRoomIndex(getRoomIndex());
    	return pref;
    }
    public boolean isSame(Preference other) {
    	if (other==null || !(other instanceof BuildingPref)) return false;
    	return ToolBox.equals(getBuilding(),((BuildingPref)other).getBuilding()) && ToolBox.equals(getRoomIndex(), ((BuildingPref)other).getRoomIndex());
    }
    public boolean isSame(Preference other, PreferenceGroup level) {
    	if (other==null || !(other instanceof BuildingPref)) return false;
    	if (!ToolBox.equals(getBuilding(),((BuildingPref)other).getBuilding())) return false;
    	if (level != null && level instanceof Class_ && ((Class_)level).getNbrRooms() == 1) {
    		if (((getRoomIndex() == null || getRoomIndex() == 0) && (((BuildingPref)other).getRoomIndex() == null || ((BuildingPref)other).getRoomIndex() == 0)))
    				return true;
    	}
    	return ToolBox.equals(getRoomIndex(), ((BuildingPref)other).getRoomIndex());
    }
    
    @Override
    public String preferenceHtml(String nameFormat, boolean highlightClassPrefs) {
    	StringBuffer sb = new StringBuffer("<span ");
    	String style = "font-weight:bold;";
		if (this.getPrefLevel().getPrefId().intValue() != 4) {
			style += "color:" + this.getPrefLevel().prefcolor() + ";";
		}
		if (this.getOwner() != null && this.getOwner() instanceof Class_ && highlightClassPrefs) {
			style += "background: #ffa;";
		}
		sb.append("style='" + style + "' ");
		String owner = "";
		if (getOwner() != null && getOwner() instanceof Class_) {
			owner = " (" + MSG.prefOwnerClass() + ")";
		} else if (getOwner() != null && getOwner() instanceof SchedulingSubpart) {
			owner = " (" + MSG.prefOwnerSchedulingSubpart() + ")";
		} else if (getOwner() != null && getOwner() instanceof DepartmentalInstructor) {
			owner = " (" + MSG.prefOwnerInstructor() + ")";
		} else if (getOwner() != null && getOwner() instanceof Exam) {
			owner = " (" + MSG.prefOwnerExamination() + ")";
		} else if (getOwner() != null && getOwner() instanceof Department) {
			owner = " (" + MSG.prefOwnerDepartment() + ")";
		} else if (getOwner() != null && getOwner() instanceof Session) {
			owner = " (" + MSG.prefOwnerSession() + ")";
		}
		sb.append("onmouseover=\"showGwtRoomHint(this, '-" + getBuilding().getUniqueId() + "', '" + getPrefLevel().getPrefName() + " " + MSG.prefBuilding() + " {0}" + owner + "');\" onmouseout=\"hideGwtRoomHint();\">");
    	sb.append(this.preferenceAbbv());
    	sb.append("</span>");
    	return (sb.toString());
    }

	public String preferenceTitle() {
    	return MSG.prefTitleBuilding(getPrefLevel().getPrefName(), getBuilding().getName());
	}
	
	@Transient
	public Type getType() { return Type.BUILDING; }
	
	@Override
	public boolean appliesTo(PreferenceGroup group) {
		if (!super.appliesTo(group)) return false;
		if (getRoomIndex() != null && group instanceof Class_)
			return getRoomIndex() < ((Class_)group).getNbrRooms();
		if (getRoomIndex() != null && group instanceof SchedulingSubpart)
			return getRoomIndex() < ((SchedulingSubpart)group).getMaxRooms();
		return true;
	}
}
