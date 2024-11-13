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
package org.unitime.timetable.onlinesectioning.custom;

import org.unitime.timetable.defaults.ApplicationProperty;
import org.unitime.timetable.gwt.server.Query;
import org.unitime.timetable.model.InstrOfferingConfig;
import org.unitime.timetable.model.InstructionalMethod;
import org.unitime.timetable.model.InstructionalOffering;
import org.unitime.timetable.model.Student;
import org.unitime.timetable.model.StudentSchedulingRule;
import org.unitime.timetable.model.dao.InstructionalOfferingDAO;
import org.unitime.timetable.model.dao.StudentDAO;
import org.unitime.timetable.onlinesectioning.OnlineSectioningServer;
import org.unitime.timetable.onlinesectioning.match.CourseMatcher;
import org.unitime.timetable.onlinesectioning.match.RuleCheckingCourseMatcherProvider;
import org.unitime.timetable.onlinesectioning.model.XConfig;
import org.unitime.timetable.onlinesectioning.model.XCourseId;
import org.unitime.timetable.onlinesectioning.model.XOffering;
import org.unitime.timetable.onlinesectioning.model.XSchedulingRule;
import org.unitime.timetable.onlinesectioning.model.XStudent;
import org.unitime.timetable.onlinesectioning.server.DatabaseServer;
import org.unitime.timetable.onlinesectioning.status.StatusPageSuggestionsAction.StudentMatcher;
import org.unitime.timetable.onlinesectioning.status.db.DbFindEnrollmentInfoAction.DbStudentMatcher;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;

/**
 * @author Tomas Muller
 */
public class OnlineOnlyCourseMatcherProvider extends RuleCheckingCourseMatcherProvider {

	@Override
	public CourseMatcher getCourseMatcher(OnlineSectioningServer server, SessionContext context, Long studentId) {
		if (server != null && !(server instanceof DatabaseServer)) {
			XSchedulingRule rule = server.getSchedulingRule(studentId,
					StudentSchedulingRule.Mode.Filter,
					context.hasPermissionAnySession(server.getAcademicSession(), Right.StudentSchedulingAdvisor),
					context.hasPermissionAnySession(server.getAcademicSession(), Right.StudentSchedulingAdmin));
			if (rule != null)
				return new SchedulingRuleCourseMatcher(rule);
		} else {
			StudentSchedulingRule rule = StudentSchedulingRule.getRuleFilter(studentId, server, context);
			if (rule != null)
				return new SchedulingRuleCourseMatcher(rule);
		}

		String filter = ApplicationProperty.OnlineSchedulingParameter.value("Filter.OnlineOnlyStudentFilter", null);
		if (filter == null || filter.isEmpty()) return new FallbackCourseMatcher();
		if (server != null && !(server instanceof DatabaseServer)) {
			if (context.hasPermissionAnySession(server.getAcademicSession(), Right.StudentSchedulingAdmin)) {
				if ("true".equalsIgnoreCase(ApplicationProperty.OnlineSchedulingParameter.value("Filter.OnlineOnlyAdminOverride", "false"))) return new FallbackCourseMatcher();
			}
			if (context.hasPermissionAnySession(server.getAcademicSession(), Right.StudentSchedulingAdvisor)) {
				if ("true".equalsIgnoreCase(ApplicationProperty.OnlineSchedulingParameter.value("Filter.OnlineOnlyAdvisorOverride", "false"))) return new FallbackCourseMatcher();
			}
			XStudent student = server.getStudent(studentId);
			if (student == null) return new FallbackCourseMatcher();
			if (new Query(filter).match(new StudentMatcher(student, server.getAcademicSession().getDefaultSectioningStatus(), server, false)))
				return new OnlineOnlyCourseMatcher(
						ApplicationProperty.OnlineSchedulingParameter.value("Filter.OnlineOnlyInstructionalModeRegExp"),
						ApplicationProperty.OnlineSchedulingParameter.value("Filter.OnlineOnlyCourseNameRegExp")
						);
			else if ("true".equalsIgnoreCase(ApplicationProperty.OnlineSchedulingParameter.value("Filter.OnlineOnlyExclusiveCourses", "false")))
				return new NotOnlineOnlyCourseMatcher(
						ApplicationProperty.OnlineSchedulingParameter.value("Filter.ResidentialInstructionalModeRegExp"),
						ApplicationProperty.OnlineSchedulingParameter.value("Filter.OnlineOnlyCourseNameRegExp"));
			return new FallbackCourseMatcher();
		} else {
			Student student = StudentDAO.getInstance().get(studentId);
			if (student == null) return new FallbackCourseMatcher();
			if (context.hasPermissionAnySession(student.getSession(), Right.StudentSchedulingAdmin)) {
				if ("true".equalsIgnoreCase(ApplicationProperty.OnlineSchedulingParameter.value("Filter.OnlineOnlyAdminOverride", "false"))) return new FallbackCourseMatcher();
			}
			if (context.hasPermissionAnySession(student.getSession(), Right.StudentSchedulingAdvisor)) {
				if ("true".equalsIgnoreCase(ApplicationProperty.OnlineSchedulingParameter.value("Filter.OnlineOnlyAdvisorOverride", "false"))) return new FallbackCourseMatcher();
			}
			if (new Query(filter).match(new DbStudentMatcher(student)))
				return new OnlineOnlyCourseMatcher(
						ApplicationProperty.OnlineSchedulingParameter.value("Filter.OnlineOnlyInstructionalModeRegExp"),
						ApplicationProperty.OnlineSchedulingParameter.value("Filter.OnlineOnlyCourseNameRegExp")
						);
			else if ("true".equalsIgnoreCase(ApplicationProperty.OnlineSchedulingParameter.value("Filter.OnlineOnlyExclusiveCourses", "false")))
				return new NotOnlineOnlyCourseMatcher(
						ApplicationProperty.OnlineSchedulingParameter.value("Filter.ResidentialInstructionalModeRegExp"),
						ApplicationProperty.OnlineSchedulingParameter.value("Filter.OnlineOnlyCourseNameRegExp"));
			return new FallbackCourseMatcher();
		}
	}

	public static class OnlineOnlyCourseMatcher extends FallbackCourseMatcher {
		private static final long serialVersionUID = 1L;
		private String iInstructionalMode;
		private String iCourseRegExp;
		
		public OnlineOnlyCourseMatcher(String im, String cn) {
			super();
			iInstructionalMode = im;
			iCourseRegExp = cn;
		}

		@Override
		public boolean match(XCourseId course) {
			if (iCourseRegExp != null && !iCourseRegExp.isEmpty() && !course.getCourseName().matches(iCourseRegExp)) {
				return false;
			} else if (iInstructionalMode != null) {
				if (getServer() != null && !(getServer() instanceof DatabaseServer)) {
					XOffering offering = getServer().getOffering(course.getOfferingId());
					if (offering != null) {
						for (XConfig config: offering.getConfigs()) {
		        			if (iInstructionalMode.isEmpty()) {
		        				if (config.getInstructionalMethod() == null || config.getInstructionalMethod().getReference() == null || config.getInstructionalMethod().getReference().isEmpty()) {
		        					if (iShowDisabled || isEnabledForStudentScheduling(config)) return true;
		        				}
		        			} else {
		        				if (config.getInstructionalMethod() != null && config.getInstructionalMethod().getReference() != null && config.getInstructionalMethod().getReference().matches(iInstructionalMode)) {
		        					if (iShowDisabled || isEnabledForStudentScheduling(config)) return true;
		        				}
		        			}
		        		}
					}
				} else {
					InstructionalOffering offering = InstructionalOfferingDAO.getInstance().get(course.getOfferingId());
					if (offering != null) {
						for (InstrOfferingConfig config: offering.getInstrOfferingConfigs()) {
							InstructionalMethod configIm = config.getEffectiveInstructionalMethod();
							if (iInstructionalMode.isEmpty()) {
								if (configIm == null || configIm.getReference() == null || configIm.getReference().isEmpty()) {
									if (iShowDisabled || isEnabledForStudentScheduling(config)) return true;
								}
							} else {
								if (configIm != null && configIm.getReference() != null && configIm.getReference().matches(iInstructionalMode)) {
									if (iShowDisabled || isEnabledForStudentScheduling(config)) return true;
								}
							}
						}
					}
				}
				return false;
			} else {
				return (iShowDisabled || isEnabledForStudentScheduling(course));
			}
		}
	}
	
	public static class NotOnlineOnlyCourseMatcher extends FallbackCourseMatcher {
		private static final long serialVersionUID = 1L;
		private String iInstructionalMode;
		private String iCourseRegExp;
		
		public NotOnlineOnlyCourseMatcher(String im, String cn) {
			super();
			iInstructionalMode = im;
			iCourseRegExp = cn;
		}

		@Override
		public boolean match(XCourseId course) {
			if (iCourseRegExp != null && !iCourseRegExp.isEmpty() && course.getCourseName().matches(iCourseRegExp)) {
				return false;
			} else if (iInstructionalMode != null) {
				if (getServer() != null && !(getServer() instanceof DatabaseServer)) {
					XOffering offering = getServer().getOffering(course.getOfferingId());
					if (offering != null) {
						for (XConfig config: offering.getConfigs()) {
		        			if (iInstructionalMode.isEmpty()) {
		        				if (config.getInstructionalMethod() == null || config.getInstructionalMethod().getReference() == null || config.getInstructionalMethod().getReference().isEmpty()) {
		        					if (iShowDisabled || isEnabledForStudentScheduling(config)) return true;
		        				}
		        			} else {
		        				if (config.getInstructionalMethod() != null && config.getInstructionalMethod().getReference() != null && config.getInstructionalMethod().getReference().matches(iInstructionalMode)) {
		        					if (iShowDisabled || isEnabledForStudentScheduling(config)) return true;
		        				}
		        			}
		        		}
					}
				} else {
					InstructionalOffering offering = InstructionalOfferingDAO.getInstance().get(course.getOfferingId());
					if (offering != null) {
						for (InstrOfferingConfig config: offering.getInstrOfferingConfigs()) {
							InstructionalMethod configIm = config.getEffectiveInstructionalMethod();
							if (iInstructionalMode.isEmpty()) {
								if (configIm == null || configIm.getReference() == null || configIm.getReference().isEmpty()) {
									if (iShowDisabled || isEnabledForStudentScheduling(config)) return true;
								}
							} else {
								if (configIm != null && configIm.getReference() != null && configIm.getReference().matches(iInstructionalMode)) {
									if (iShowDisabled || isEnabledForStudentScheduling(config)) return true;
								}
							}
						}
					}
				}
				return false;
			} else {
				return (iShowDisabled || isEnabledForStudentScheduling(course));
			}
		}
	}
}
