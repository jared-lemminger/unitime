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
package org.unitime.timetable.dataexchange;

import java.text.SimpleDateFormat;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

import org.dom4j.Element;
import org.unitime.timetable.gwt.shared.ReservationInterface.OverrideType;
import org.unitime.timetable.model.AcademicArea;
import org.unitime.timetable.model.AcademicClassification;
import org.unitime.timetable.model.ChangeLog;
import org.unitime.timetable.model.Class_;
import org.unitime.timetable.model.CourseOffering;
import org.unitime.timetable.model.CourseReservation;
import org.unitime.timetable.model.CurriculumOverrideReservation;
import org.unitime.timetable.model.CurriculumReservation;
import org.unitime.timetable.model.GroupOverrideReservation;
import org.unitime.timetable.model.IndividualOverrideReservation;
import org.unitime.timetable.model.IndividualReservation;
import org.unitime.timetable.model.InstrOfferingConfig;
import org.unitime.timetable.model.LearningCommunityReservation;
import org.unitime.timetable.model.OverrideReservation;
import org.unitime.timetable.model.PosMajor;
import org.unitime.timetable.model.PosMinor;
import org.unitime.timetable.model.Reservation;
import org.unitime.timetable.model.SchedulingSubpart;
import org.unitime.timetable.model.Session;
import org.unitime.timetable.model.Student;
import org.unitime.timetable.model.StudentGroup;
import org.unitime.timetable.model.StudentGroupReservation;
import org.unitime.timetable.model.UniversalOverrideReservation;

/**
 * @author Tomas Muller
 */
public class ReservationImport  extends BaseImport {
	
    public void loadXml(Element root) throws Exception {
        if (!root.getName().equalsIgnoreCase("reservations")) {
        	throw new Exception("Given XML file is not reservations load file.");
        }
        try {
            beginTransaction();

            String campus = root.attributeValue("campus");
            String year   = root.attributeValue("year");
            String term   = root.attributeValue("term");
            String created = root.attributeValue("created");
            String dateFormat = root.attributeValue("dateFormat", ReservationExport.sDateFormat);
            boolean incremental = "true".equalsIgnoreCase(root.attributeValue("incremental", "false"));
	        if (incremental)
	        	info("Incremental mode.");

            Session session = Session.getSessionUsingInitiativeYearTerm(campus, year, term);
            if(session == null)
                throw new Exception("No session found for the given campus, year, and term.");

            if (created != null) {
                ChangeLog.addChange(getHibSession(), getManager(), session, session, created, ChangeLog.Source.DATA_IMPORT_RESERVATIONS, ChangeLog.Operation.UPDATE, null, null);
            }
            
            if (!incremental) {
            	info("Deleting all existing reservations...");
            	for (Reservation r: getHibSession().createQuery("select r from Reservation r where r.instructionalOffering.session.uniqueId=:sessionId", Reservation.class).
                	setParameter("sessionId", session.getUniqueId()).list()) {
            		getHibSession().remove(r);
            	}
            } else {
            	Set<String> courses = new HashSet<String>();
            	for (Iterator i = root.elementIterator(); i.hasNext(); ) {
                    Element reservationElement = (Element) i.next();
                    courses.add(reservationElement.attributeValue("subject") + "|" + reservationElement.attributeValue("courseNbr") + "|" + reservationElement.attributeValue("type", "course"));
            	}
            	info("Deleting existing reservations for courses & types listed in the XML file...");
            	int count = 0;
            	for (Reservation r: getHibSession().createQuery("select r from Reservation r where r.instructionalOffering.session.uniqueId=:sessionId", Reservation.class).
                    	setParameter("sessionId", session.getUniqueId()).list()) {
            		boolean hasCourse = false;
            		String type = getType(r);
            		for (CourseOffering co: r.getInstructionalOffering().getCourseOfferings()) {
            			if (courses.contains(co.getSubjectAreaAbbv() + "|" + co.getCourseNbr() + "|" + type)) {
            				hasCourse = true;
            				break;
            			}
            		}
            		if (hasCourse) {
            			getHibSession().remove(r);
            			count ++;
            		}
            	}
            	info(count + " reservations removed.");
            }
        	flush(false);
            
        	info("Loading areas, majors, classifications, and student groups...");
        	
        	Hashtable<String, AcademicArea> areasByAbbv = new Hashtable<String, AcademicArea>();
        	Hashtable<String, AcademicArea> areasByExtId = new Hashtable<String, AcademicArea>();
        	for (AcademicArea area: getHibSession().createQuery(
        			"select a from AcademicArea a where a.session.uniqueId = :sessionId", AcademicArea.class)
        			.setParameter("sessionId", session.getUniqueId()).list()) {
        		areasByAbbv.put(area.getAcademicAreaAbbreviation(), area);
        		if (area.getExternalUniqueId() != null)
        			areasByExtId.put(area.getExternalUniqueId(), area);
        	}
           
        	Hashtable<String, StudentGroup> groupsByCode = new Hashtable<String, StudentGroup>();
        	Hashtable<String, StudentGroup> groupsByExtId = new Hashtable<String, StudentGroup>();
        	for (StudentGroup group: getHibSession().createQuery(
        			"select a from StudentGroup a where a.session.uniqueId = :sessionId", StudentGroup.class)
        			.setParameter("sessionId", session.getUniqueId()).list()) {
        		groupsByCode.put(group.getGroupAbbreviation(), group);
        		if (group.getExternalUniqueId() != null)
        			groupsByExtId.put(group.getExternalUniqueId(), group);
        	}

        	Hashtable<String, PosMajor> majorsByCode = new Hashtable<String, PosMajor>();
        	Hashtable<String, PosMajor> majorsByExtId = new Hashtable<String, PosMajor>();
        	for (PosMajor major: getHibSession().createQuery(
        			"select a from PosMajor a where a.session.uniqueId = :sessionId", PosMajor.class)
        			.setParameter("sessionId", session.getUniqueId()).list()) {
        		for (AcademicArea area: major.getAcademicAreas())
        			majorsByCode.put(area.getAcademicAreaAbbreviation() + "|" + major.getCode(), major);
        		if (major.getExternalUniqueId() != null)
        			majorsByExtId.put(major.getExternalUniqueId(), major);
        	}
        	
        	Hashtable<String, PosMinor> minorsByCode = new Hashtable<String, PosMinor>();
        	Hashtable<String, PosMinor> minorsByExtId = new Hashtable<String, PosMinor>();
        	for (PosMinor minor: getHibSession().createQuery(
        			"select a from PosMinor a where a.session.uniqueId = :sessionId", PosMinor.class)
        			.setParameter("sessionId", session.getUniqueId()).list()) {
        		for (AcademicArea area: minor.getAcademicAreas())
        			minorsByCode.put(area.getAcademicAreaAbbreviation() + "|" + minor.getCode(), minor);
        		if (minor.getExternalUniqueId() != null)
        			minorsByExtId.put(minor.getExternalUniqueId(), minor);
        	}

        	Hashtable<String, AcademicClassification> clasfsByCode = new Hashtable<String, AcademicClassification>();
        	Hashtable<String, AcademicClassification> clasfsByExtId = new Hashtable<String, AcademicClassification>();
        	for (AcademicClassification clasf: getHibSession().createQuery(
        			"select a from AcademicClassification a where a.session.uniqueId = :sessionId", AcademicClassification.class)
        			.setParameter("sessionId", session.getUniqueId()).list()) {
        		clasfsByCode.put(clasf.getCode(), clasf);
        		if (clasf.getExternalUniqueId() != null)
        			clasfsByExtId.put(clasf.getExternalUniqueId(), clasf);
        	}

        	info("Loading courses...");
        	Hashtable<String, CourseOffering> corusesByExtId = new Hashtable<String, CourseOffering>();
        	Hashtable<String, CourseOffering> corusesBySubjectCourseNbr = new Hashtable<String, CourseOffering>();
        	for (CourseOffering course: getHibSession().createQuery(
        			"select a from CourseOffering a where a.subjectArea.session.uniqueId = :sessionId", CourseOffering.class)
        			.setParameter("sessionId", session.getUniqueId()).list()) {
        		corusesBySubjectCourseNbr.put(course.getSubjectArea().getSubjectAreaAbbreviation() + "|" + course.getCourseNbr(), course);
        		if (course.getExternalUniqueId() != null)
        			corusesByExtId.put(course.getExternalUniqueId(), course);
        	}
        	
        	SimpleDateFormat df = new SimpleDateFormat(dateFormat, Locale.US);
        	
        	info("Importing reservations...");
        	for (Iterator i = root.elementIterator(); i.hasNext(); ) {
                Element reservationElement = (Element) i.next();
                
                Reservation reservation = null;
                String type = reservationElement.attributeValue("type", "course");
                if ("individual".equals(type)) {
                	reservation = new IndividualReservation();
                	if ("true".equalsIgnoreCase(reservationElement.attributeValue("override"))) {
                		reservation = new IndividualOverrideReservation();
                		((IndividualOverrideReservation)reservation).setAlwaysExpired("true".equalsIgnoreCase(reservationElement.attributeValue("expired")));
                		((IndividualOverrideReservation)reservation).setAllowOverlap("true".equalsIgnoreCase(reservationElement.attributeValue("allowOverlap")));
                		((IndividualOverrideReservation)reservation).setCanAssignOverLimit("true".equalsIgnoreCase(reservationElement.attributeValue("overLimit")));
                		((IndividualOverrideReservation)reservation).setMustBeUsed("true".equalsIgnoreCase(reservationElement.attributeValue("mustBeUsed")));
                	}
                } else if ("group".equals(type)) {
                	reservation = new StudentGroupReservation();
                	if ("true".equalsIgnoreCase(reservationElement.attributeValue("override"))) {
                		reservation = new GroupOverrideReservation();
                		((GroupOverrideReservation)reservation).setAlwaysExpired("true".equalsIgnoreCase(reservationElement.attributeValue("expired")));
                		((GroupOverrideReservation)reservation).setAllowOverlap("true".equalsIgnoreCase(reservationElement.attributeValue("allowOverlap")));
                		((GroupOverrideReservation)reservation).setCanAssignOverLimit("true".equalsIgnoreCase(reservationElement.attributeValue("overLimit")));
                		((GroupOverrideReservation)reservation).setMustBeUsed("true".equalsIgnoreCase(reservationElement.attributeValue("mustBeUsed")));
                	}
                } else if ("curriculum".equals(type)) {
                	reservation = new CurriculumReservation();
                	if ("true".equalsIgnoreCase(reservationElement.attributeValue("override"))) {
                		reservation = new CurriculumOverrideReservation();
                		((CurriculumOverrideReservation)reservation).setAlwaysExpired("true".equalsIgnoreCase(reservationElement.attributeValue("expired")));
                		((CurriculumOverrideReservation)reservation).setAllowOverlap("true".equalsIgnoreCase(reservationElement.attributeValue("allowOverlap")));
                		((CurriculumOverrideReservation)reservation).setCanAssignOverLimit("true".equalsIgnoreCase(reservationElement.attributeValue("overLimit")));
                		((CurriculumOverrideReservation)reservation).setMustBeUsed("true".equalsIgnoreCase(reservationElement.attributeValue("mustBeUsed")));
                	}
                } else if ("course".equals(type)) {
                	reservation = new CourseReservation();
                } else if ("lc".equals(type)) {
                	reservation = new LearningCommunityReservation();
                } else if ("universal".equals(type)) {
                	reservation = new UniversalOverrideReservation();
            		((UniversalOverrideReservation)reservation).setAlwaysExpired("true".equalsIgnoreCase(reservationElement.attributeValue("expired")));
            		((UniversalOverrideReservation)reservation).setAllowOverlap("true".equalsIgnoreCase(reservationElement.attributeValue("allowOverlap")));
            		((UniversalOverrideReservation)reservation).setCanAssignOverLimit("true".equalsIgnoreCase(reservationElement.attributeValue("overLimit")));
            		((UniversalOverrideReservation)reservation).setMustBeUsed("true".equalsIgnoreCase(reservationElement.attributeValue("mustBeUsed")));
                } else {
                	for (OverrideType t: OverrideType.values()) {
                		if (t.getReference().equalsIgnoreCase(type)) {
                			reservation = new OverrideReservation();
                			((OverrideReservation)reservation).setOverrideType(t);
                			break;
                		}
                	}
                	if (reservation == null) {
                		warn("Unknown reservation type " + type);
                		continue;
                	}
                }
                
                CourseOffering course = corusesBySubjectCourseNbr.get(
                		reservationElement.attributeValue("subject") + "|" + reservationElement.attributeValue("courseNbr"));
                if (course == null || course.getInstructionalOffering() == null) {
                	warn("Unknown course " + reservationElement.attributeValue("subject") + " " + reservationElement.attributeValue("courseNbr"));
                	continue;
                }
                reservation.setInstructionalOffering(course.getInstructionalOffering());
                
                String limit = reservationElement.attributeValue("limit");
                if (limit != null) {
                	try {
                		reservation.setLimit(Integer.parseInt(limit));
                	} catch (NumberFormatException e) {
                		warn("Unable to parse reservation limit " + limit);
                	}
                }
                
                String expire = reservationElement.attributeValue("expire");
                if (expire != null) {
                	try {
                		reservation.setExpirationDate(df.parse(expire));
                	} catch (Exception e) {
                		warn("Unable to parse reservation expiration date " + expire);
                	}
                }
                
                String startDate = reservationElement.attributeValue("startDate");
                if (startDate != null) {
                	try {
                		reservation.setStartDate(df.parse(startDate));
                	} catch (Exception e) {
                		warn("Unable to parse reservation start date " + expire);
                	}
                }
                
                String inclusive = reservationElement.attributeValue("inclusive");
                if (inclusive != null) {
                	reservation.setInclusive("true".equalsIgnoreCase(inclusive));
                }
                
                reservation.setConfigurations(new HashSet<InstrOfferingConfig>());
                for (Iterator j = reservationElement.elementIterator("configuration"); j.hasNext(); ) {
                	String name = ((Element)j.next()).attributeValue("name");
                	InstrOfferingConfig config = null;
                	for (InstrOfferingConfig c: course.getInstructionalOffering().getInstrOfferingConfigs()) {
                		if (name.equals(c.getName())) { config = c; break; }
                	}
                	if (config == null) {
                		warn("Unable to find configuration " + name + " of course " + course.getCourseName());
                	} else {
                		reservation.getConfigurations().add(config);
                	}
                }
                
                reservation.setClasses(new HashSet<Class_>());
                for (Iterator j = reservationElement.elementIterator("class"); j.hasNext(); ) {
                	Element classEl = (Element)j.next();
                	String extId = classEl.attributeValue("externalId");
                	String itype = classEl.attributeValue("type");
                	String suffix = classEl.attributeValue("suffix");
                	Class_ clazz = null;
                	search: for (InstrOfferingConfig c: course.getInstructionalOffering().getInstrOfferingConfigs()) {
                		for (SchedulingSubpart s: c.getSchedulingSubparts()) {
                			if (itype != null && !itype.equals(s.getItypeDesc().trim())) continue;
                			for (Class_ z: s.getClasses()) {
                				if (extId != null && extId.equals(z.getExternalUniqueId())) { clazz = z; break search; }
                				if (extId == null && suffix != null && suffix.equals(getClassSuffix(z))) { clazz = z; break search; } 
                			}
                		}
                	}
                	if (clazz == null) {
                		warn("Unable to find clazz " + (extId == null ? itype + " " + suffix : extId) + " of course " + course.getCourseName());
                	} else {
                		reservation.getClasses().add(clazz);
                	}
                }
                
                if ("individual".equals(type)) {
                	IndividualReservation individual = (IndividualReservation)reservation;
                	individual.setStudents(new HashSet<Student>());
                	for (Iterator j = reservationElement.elementIterator("student"); j.hasNext(); ) {
                    	String studentId = ((Element)j.next()).attributeValue("externalId");
                    	Student student = Student.findByExternalId(session.getUniqueId(), studentId);
                    	if (student == null) {
                    		warn("Unable to find student " + student);
                    	} else {
                    		individual.getStudents().add(student);
                    	}
                	}
                	if (individual.getStudents().isEmpty()) {
                		warn("Individual reservation of course " + course.getCourseName() + " has no students.");
                		continue;
                	}
                } else if ("group".equals(type)) {
                	StudentGroupReservation group = (StudentGroupReservation)reservation;
                    for (Iterator j = reservationElement.elementIterator("studentGroup"); j.hasNext(); ) {
                    	Element groupEl = (Element)j.next();
                    	String extId = groupEl.attributeValue("externalId");
                    	String code = groupEl.attributeValue("code");
                    	StudentGroup sg = (extId == null ? groupsByCode.get(code) : groupsByExtId.get(extId));
                    	if (sg == null) {
                    		warn("Unable to find student group " + (extId == null ? code : extId));
                    	} else {
                    		group.setGroup(sg);
                    		break;
                    	}
                    }
                    if (group.getGroup() == null) {
                    	warn("Group reservation of course " + course.getCourseName() + " has no student group.");
                    }
                } else if ("lc".equals(type)) {
                	LearningCommunityReservation lc = (LearningCommunityReservation)reservation;
                    for (Iterator j = reservationElement.elementIterator("studentGroup"); j.hasNext(); ) {
                    	Element groupEl = (Element)j.next();
                    	String extId = groupEl.attributeValue("externalId");
                    	String code = groupEl.attributeValue("code");
                    	StudentGroup sg = (extId == null ? groupsByCode.get(code) : groupsByExtId.get(extId));
                    	if (sg == null) {
                    		warn("Unable to find student group " + (extId == null ? code : extId));
                    	} else {
                    		lc.setGroup(sg);
                    		break;
                    	}
                    }
                    lc.setCourse(course);
                    if (lc.getGroup() == null) {
                    	warn("Group reservation of course " + course.getCourseName() + " has no student group.");
                    }
                } else if ("curriculum".equals(type)) {
                	CurriculumReservation curriculum = (CurriculumReservation)reservation;
                	curriculum.setAreas(new HashSet<AcademicArea>());
                    for (Iterator j = reservationElement.elementIterator("academicArea"); j.hasNext(); ) {
                    	Element areaEl = (Element)j.next();
                    	String extId = areaEl.attributeValue("externalId");
                    	String abbv = areaEl.attributeValue("abbreviation");
                    	AcademicArea area = (extId == null ? areasByAbbv.get(abbv) : areasByExtId.get(extId));
                    	if (area == null) {
                    		warn("Unable to find academic area " + (extId == null ? area : extId));
                    	} else {
                    		curriculum.getAreas().add(area);
                    	}
                    }
                    if (curriculum.getAreas().isEmpty()) {
                    	warn("Curriculum reservation of course " + course.getCourseName() + " has no academic area.");
                    }
                    curriculum.setClassifications(new HashSet<AcademicClassification>());
                    for (Iterator j = reservationElement.elementIterator("academicClassification"); j.hasNext(); ) {
                    	Element clasfEl = (Element)j.next();
                    	String extId = clasfEl.attributeValue("externalId");
                    	String code = clasfEl.attributeValue("code");
                    	AcademicClassification clasf = (extId == null ? clasfsByCode.get(code) : clasfsByExtId.get(extId));
                    	if (clasf == null) {
                    		warn("Unable to find academic classification " + (extId == null ? code : extId));
                    	} else {
                    		curriculum.getClassifications().add(clasf);
                    	}
                    }
                    curriculum.setMajors(new HashSet<PosMajor>());
                    for (Iterator j = reservationElement.elementIterator("major"); j.hasNext(); ) {
                    	Element majorEl = (Element)j.next();
                    	String extId = majorEl.attributeValue("externalId");
                    	String code = majorEl.attributeValue("code");
                    	PosMajor major = (extId != null ? majorsByExtId.get(extId) : null);
                    	if (major == null)
                    		for (AcademicArea area: curriculum.getAreas()) {
                    			major = majorsByCode.get(area.getAcademicAreaAbbreviation() + "|" + code);
                    			if (major != null) break;
                    		}
                    	if (major == null) {
                    		warn("Unable to find major " + (extId == null ? code : extId));
                    	} else {
                    		curriculum.getMajors().add(major);
                    	}
                    }
                    curriculum.setMinors(new HashSet<PosMinor>());
                    for (Iterator j = reservationElement.elementIterator("minor"); j.hasNext(); ) {
                    	Element minorEl = (Element)j.next();
                    	String extId = minorEl.attributeValue("externalId");
                    	String code = minorEl.attributeValue("code");
                    	PosMinor minor = (extId != null ? minorsByExtId.get(extId) : null);
                    	if (minor == null)
                    		for (AcademicArea area: curriculum.getAreas()) {
                    			minor = minorsByCode.get(area.getAcademicAreaAbbreviation() + "|" + code);
                    			if (minor != null) break;
                    		}
                    	if (minor == null) {
                    		warn("Unable to find major " + (extId == null ? code : extId));
                    	} else {
                    		curriculum.getMinors().add(minor);
                    	}
                    }
                } else if ("course".equals(type)) {
                	course.setReservation(reservation.getLimit()); reservation.setLimit(null);
                	getHibSession().merge(course);
                	if (reservation.getConfigurations().isEmpty() && reservation.getClasses().isEmpty()) continue;
                	((CourseReservation)reservation).setCourse(course);
                } else if ("universal".equals(type)) {
                	((UniversalOverrideReservation)reservation).setFilter(reservationElement.attributeValue("filter"));
                } else {
                	OverrideReservation override = (OverrideReservation)reservation;
                	override.setStudents(new HashSet<Student>());
                	for (Iterator j = reservationElement.elementIterator("student"); j.hasNext(); ) {
                    	String studentId = ((Element)j.next()).attributeValue("externalId");
                    	Student student = Student.findByExternalId(session.getUniqueId(), studentId);
                    	if (student == null) {
                    		warn("Unable to find student " + student);
                    	} else {
                    		override.getStudents().add(student);
                    	}
                	}
                	if (override.getStudents().isEmpty()) {
                		warn("Override reservation of course " + course.getCourseName() + " has no students.");
                		continue;
                	}
                }
                
                getHibSession().persist(reservation);
            }
        	
        	info("All done.");
        	
            commitTransaction();
        } catch (Exception e) {
            fatal("Exception: " + e.getMessage(), e);
            rollbackTransaction();
            throw e;
        }
	}
    
    private String getType(Reservation reservation) {
    	if (reservation instanceof OverrideReservation) {
    		return ((OverrideReservation)reservation).getOverrideType().getReference().toLowerCase();
    	} else if (reservation instanceof IndividualReservation) {
    		return "individual";
    	} else if (reservation instanceof LearningCommunityReservation) {
    		return "lc";
    	} else if (reservation instanceof StudentGroupReservation) {
    		return "group";
    	} else if (reservation instanceof CurriculumReservation) {
    		return "curriculum";
    	} else if (reservation instanceof CourseReservation) {
    		return "course";
    	} else if (reservation instanceof UniversalOverrideReservation) {
    		return "universal";
    	} else {
    		return "unknown";
    	}
    }
    
}
