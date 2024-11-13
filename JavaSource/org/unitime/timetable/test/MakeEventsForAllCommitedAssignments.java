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
package org.unitime.timetable.test;

import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.cpsolver.ifs.util.ToolBox;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.unitime.commons.hibernate.util.HibernateUtil;
import org.unitime.timetable.model.Assignment;
import org.unitime.timetable.model.ClassEvent;
import org.unitime.timetable.model.EventDateMapping;
import org.unitime.timetable.model.Solution;
import org.unitime.timetable.model.dao._RootDAO;


/**
 * @author Tomas Muller
 */
public class MakeEventsForAllCommitedAssignments {

    public static void main(String args[]) {
        try {
            ToolBox.configureLogging();
            HibernateUtil.configureHibernate(new Properties());
            
            Session hibSession = new _RootDAO().getSession();
            List<Solution> commitedSolutions = hibSession.createQuery("select s from Solution s where s.commited = true", Solution.class).list();
            
            int idx = 0;
            
            for (Iterator<Solution> i=commitedSolutions.iterator();i.hasNext();) {
                Solution s = i.next();
                EventDateMapping.Class2EventDateMap class2eventDates = EventDateMapping.getMapping(s.getSession().getUniqueId());
            
                idx++;
                
                System.out.println("Procession solution "+idx+"/"+commitedSolutions.size()+" ("+s.getOwner().getName()+" of "+s.getSession().getLabel()+", committed "+s.getCommitDate()+")");
                
                Transaction tx = null;
                try {
                    tx = hibSession.beginTransaction();
                    
                    for (ClassEvent e: hibSession.createQuery(
                            "select e from Solution s inner join s.assignments a, ClassEvent e where e.clazz=a.clazz and s.uniqueId=:solutionId", ClassEvent.class)
                            .setParameter("solutionId", s.getUniqueId())
                            .list()) {
                        hibSession.remove(e);
                    }
                    for (Assignment a: hibSession.createQuery(
                            "select a from Assignment a "+
                            "where a.solution.uniqueId = :solutionId", Assignment.class)
                            .setParameter("solutionId", s.getUniqueId())
                            .list()) {
                        ClassEvent event = a.generateCommittedEvent(null, true, class2eventDates);
                        if (event != null && !event.getMeetings().isEmpty()) {
                            System.out.println("  "+a.getClassName()+" "+a.getPlacement().getLongName(true));
                            if (event.getUniqueId() == null)
                            	hibSession.persist(event);
                            else
                            	hibSession.merge(event);
                        }
            		    if (event != null && event.getMeetings().isEmpty() && event.getUniqueId() != null)
            		    	hibSession.remove(event);
                    }
                    
                    tx.commit();
                } catch (Exception e) {
                    if (tx!=null) tx.rollback();
                    throw e;
                }
                
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
