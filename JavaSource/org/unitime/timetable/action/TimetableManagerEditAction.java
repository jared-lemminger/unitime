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
package org.unitime.timetable.action;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.apache.struts2.convention.annotation.Action;
import org.apache.struts2.convention.annotation.Result;
import org.apache.struts2.tiles.annotation.TilesDefinition;
import org.apache.struts2.tiles.annotation.TilesDefinitions;
import org.apache.struts2.tiles.annotation.TilesPutAttribute;
import org.hibernate.Transaction;
import org.unitime.commons.Debug;
import org.unitime.localization.impl.Localization;
import org.unitime.localization.messages.CourseMessages;
import org.unitime.timetable.defaults.ApplicationProperty;
import org.unitime.timetable.form.TimetableManagerForm;
import org.unitime.timetable.interfaces.ExternalUidLookup;
import org.unitime.timetable.interfaces.ExternalUidLookup.UserInfo;
import org.unitime.timetable.model.ChangeLog;
import org.unitime.timetable.model.Department;
import org.unitime.timetable.model.ManagerRole;
import org.unitime.timetable.model.Roles;
import org.unitime.timetable.model.Session;
import org.unitime.timetable.model.SolverGroup;
import org.unitime.timetable.model.TimetableManager;
import org.unitime.timetable.model.comparators.RolesComparator;
import org.unitime.timetable.model.dao.DepartmentDAO;
import org.unitime.timetable.model.dao.RolesDAO;
import org.unitime.timetable.model.dao.SessionDAO;
import org.unitime.timetable.model.dao.SolverGroupDAO;
import org.unitime.timetable.model.dao.TimetableManagerDAO;
import org.unitime.timetable.security.rights.Right;
import org.unitime.timetable.util.IdValue;
import org.unitime.timetable.util.LookupTables;

/**
 * @author Tomas Muller, Stephanie Schluttenhofer, Heston Fernandes
 */
@Action(value = "timetableManagerEdit", results = {
		@Result(name = "addManagerInfo", type = "tiles", location = "timetableManagerAdd.tiles"),
		@Result(name = "editManagerInfo", type = "tiles", location = "timetableManagerEdit.tiles"),
		@Result(name = "displayManagerList", type = "redirect", location="/timetableManagerList.action", params = {
				"anchor", "${form.uniqueId}"})
	})
@TilesDefinitions({
@TilesDefinition(name = "timetableManagerAdd.tiles", extend = "baseLayout", putAttributes =  {
		@TilesPutAttribute(name = "title", value = "Add Timetable Manager"),
		@TilesPutAttribute(name = "body", value = "/admin/timetableManagerEdit.jsp")
	}),
@TilesDefinition(name = "timetableManagerEdit.tiles", extend = "baseLayout", putAttributes =  {
		@TilesPutAttribute(name = "title", value = "Edit Timetable Manager"),
		@TilesPutAttribute(name = "body", value = "/admin/timetableManagerEdit.jsp")
	})
})
public class TimetableManagerEditAction extends UniTimeAction<TimetableManagerForm> {
	private static final long serialVersionUID = 3071423315785922315L;
	protected static final CourseMessages MSG = Localization.create(CourseMessages.class);
	
	private Long id;
	private String deleteType;
	private Integer deleteId;
	
	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }
	public String getDeleteType() { return deleteType; }
	public void setDeleteType(String deleteType) { this.deleteType = deleteType; }
	public Integer getDeleteId() { return deleteId; }
	public void setDeleteId(Integer deleteId) { this.deleteId = deleteId; }

	@Override
    public String execute() throws Exception {
		if (form == null) form = new TimetableManagerForm();

        // Check access
    	sessionContext.checkPermission(Right.TimetableManagers);

        // Read Operation
    	if (op == null) op = form.getOp();
	        
		if (op==null || op.trim().isEmpty())
		    throw new Exception ("Operation could not be interpreted: " + op);

		// Set up Departments
		LookupTables.setupDepts(request, sessionContext.getUser().getCurrentAcademicSessionId());
		request.setAttribute("solverGroupList", SolverGroup.findBySessionId(sessionContext.getUser().getCurrentAcademicSessionId()));
        form.setOp(op);

        // Back
        if (MSG.actionBackToManagers().equals(op)) {
            return "displayManagerList";
        }
        
        // Redirect from Manager List - Edit Manager
        if ("Edit".equals(op)) {
        	loadForm();
        	setupOtherSessions(true);
        }
        
        // Redirect from Manager List - Add Manager
        if (MSG.actionAddTimetableManager().equals(op)) {
        	sessionContext.checkPermission(Right.TimetableManagerAdd);
            form.setLookupEnabled(ApplicationProperty.ManagerExternalIdLookup.isTrue() && ApplicationProperty.ManagerExternalIdLookupClass.value() != null);
            setupOtherSessions(true);
        }
        
        // Lookup puid / career account
        if (MSG.actionLookupManager().equals(op)) {
            // Do nothing - taken care below
        }

        // Add Role
        if (MSG.actionAddRole().equals(op)) {
        	form.validate(this);
        	if (!hasFieldErrors() && !form.getRoles().contains(form.getRole())) {
                Roles role = RolesDAO.getInstance().get(form.getRole());
                form.addToRoles(role); 
                if (form.getRoles().size()==1)
                    form.setPrimaryRole(role.getRoleId());
                form.getRoleReceiveEmailFlags().add(true);
            }
        }
        
        // Add Department
        if (MSG.actionAddDepartment().equals(op)) {
        	form.validate(this);
        	if (!hasFieldErrors() && !form.getDepts().contains(form.getDept())) {
                Department dept = DepartmentDAO.getInstance().get(form.getDept());            
                form.addToDepts(dept);    
            }
        }
        
        // Add Solver Group
        if (MSG.actionAddSolverGroup().equals(op)) {
        	form.validate(this);
        	if (!hasFieldErrors() && !form.getSolverGrs().contains(form.getSolverGr())) {
                SolverGroup sg = SolverGroupDAO.getInstance().get(form.getSolverGr());            
                form.addToSolverGrs(sg);    
            }
        }

        // Add new manager
        if (MSG.actionSaveManager().equals(op)) {
        	form.validate(this);
        	if (!hasFieldErrors()) {
                addManager();
                return "displayManagerList";
            }
        }
        
        // Update Manager
        if (MSG.actionUpdateManager().equals(op)) {
        	form.validate(this);
        	if (!hasFieldErrors()) {
                updateManager();
                return "displayManagerList";
            }
        }
        
        if (MSG.actionDelete().equals(op) && deleteId != null && deleteType != null && !deleteType.isEmpty()) {
            if (deleteType.equalsIgnoreCase("dept")) {
                form.removeFromDepts(deleteId);
            }
            if (deleteType.equalsIgnoreCase("solverGr")) {
                form.removeFromSolverGrs(deleteId);
            }
            if (deleteType.equalsIgnoreCase("role")) {
                form.removeFromRoles(deleteId);
                if (form.getRoles().size()==1)
                    form.setPrimaryRole(form.getRoles(0));
                if (form.getRoles().size() > 1 && (form.getPrimaryRole() == null || !form.getRoles().contains(form.getPrimaryRole())))
                	form.setPrimaryRole(form.getRoles(0));
            }
        }
        
        // Delete Manager
        if (MSG.actionDeleteManager().equals(op) && deleteId == null && (deleteType == null || deleteType.isEmpty())) {
            deleteManager();
            return "displayManagerList";
        }

        // Get manager details
        lookupManager();
        // Get roles not already assigned
        setupRoles();
        if (form.getOtherSessions() == null)
        	setupOtherSessions(false);
        
        return (form.getUniqueId() == null || form.getUniqueId() < 0 ? "addManagerInfo" : "editManagerInfo");
    }

    /**
     * Lookup manager details from I2A2
     */
    private void lookupManager() throws Exception{
    	try {
    		String id = form.getExternalId();
            if (id!=null && id.trim().length()>0 && form.getLookupEnabled().booleanValue()) {
                
            	String className = ApplicationProperty.ManagerExternalIdLookupClass.value();        	
            	ExternalUidLookup lookup = (ExternalUidLookup) (Class.forName(className).getDeclaredConstructor().newInstance());
           		UserInfo results = lookup.doLookup(id);
           		if (results == null) return;
    			form.setExternalId(results.getExternalId());
    			form.setLookupResult(results.getUserName());
    			if (form.getFirstName() == null || form.getFirstName().trim().length() == 0){
    				form.setFirstName(results.getFirstName());
    			}
    			if (form.getMiddleName() == null || form.getMiddleName().trim().length() == 0){
    				form.setMiddleName(results.getMiddleName());
    			}
    			if (form.getLastName() == null || form.getLastName().trim().length() == 0){
    				form.setLastName(results.getLastName());
    			}
    			if (form.getTitle() == null || form.getTitle().trim().length() == 0){
    				form.setTitle(results.getAcademicTitle());
    			}
    			if (form.getEmail() == null || form.getEmail().trim().length() == 0){
    				form.setEmail(results.getEmail());
    			}
            }
    	} catch (Exception e) {
    		Debug.error(MSG.errorLookupManager(e.getMessage()), e);
    		addFieldError("form.externalId", MSG.errorLookupManager(e.getMessage()));
    	}
    }

    /**
     * Display only those roles not already assigned to the manager
     * @param request
     */
    private void setupRoles() {
        Set<Roles> roles = Roles.findAll(true);
        
        if (!sessionContext.hasPermission(Right.SessionIndependent))
        	for (Iterator<Roles> i = roles.iterator(); i.hasNext(); )
        		if (i.next().hasRight(Right.SessionIndependent)) i.remove();
        
        if (form.getRoleRefs() != null && !form.getRoleRefs().isEmpty())
        	for (Iterator<Roles> i = roles.iterator(); i.hasNext(); )
        		if (form.getRoleRefs().contains(i.next().getReference()))
        			i.remove();
        
        request.setAttribute(Roles.ROLES_ATTR_NAME, roles);
    }
    
    private void setupOtherSessions(boolean defaultSelection) {
    	Long currentSessionId = sessionContext.getUser().getCurrentAcademicSessionId();
        List<IdValue> sessions = new ArrayList<IdValue>();
        TimetableManager mgr = (form.getUniqueId() == null ? null : TimetableManagerDAO.getInstance().get(form.getUniqueId()));
        List<Long> defaultSelectedSessionIds = new ArrayList<Long>();
        boolean past = true;
        for (Session session: SessionDAO.getInstance().getSession().createQuery(
        		"select s from Session s, Session z " +
        		"where z.uniqueId = :sessionId and s.academicInitiative = z.academicInitiative and (s.uniqueId = :sessionId or bitand(s.statusType.status, 7572918) > 0) " +
        		"order by s.sessionBeginDateTime", Session.class).setParameter("sessionId", currentSessionId).setCacheable(true).list()) {
        	IdValue s = new IdValue(session.getUniqueId(), session.getLabel(), null, !session.getUniqueId().equals(currentSessionId));
        	sessions.add(s);
        	if (session.getUniqueId().equals(currentSessionId)) {
        		defaultSelectedSessionIds.add(session.getUniqueId());
        		past = false;
        	} else if (defaultSelection) {
        		if (mgr == null && !past) {
        			defaultSelectedSessionIds.add(session.getUniqueId());
        		} else if (mgr != null && sameDepartments(mgr, currentSessionId, session.getUniqueId()) && sameSolverGroups(mgr, currentSessionId, session.getUniqueId())) {
        			defaultSelectedSessionIds.add(session.getUniqueId());
        		}
        	}
        }
        form.setOtherSessions(sessions);
        if (defaultSelection)
        	form.setUpdateSessions(defaultSelectedSessionIds);
    }
    
    private boolean sameDepartments(TimetableManager mgr, Long s1, Long s2) {
    	TreeSet<String> d1 = new TreeSet<String>();
    	TreeSet<String> d2 = new TreeSet<String>();
    	for (Department d: mgr.getDepartments()) {
    		if (s1.equals(d.getSessionId())) d1.add(d.getDeptCode());
    		if (s2.equals(d.getSessionId())) d2.add(d.getDeptCode());
    	}
    	return d1.equals(d2);
    }
    
    private boolean sameSolverGroups(TimetableManager mgr, Long s1, Long s2) {
    	TreeSet<String> d1 = new TreeSet<String>();
    	TreeSet<String> d2 = new TreeSet<String>();
    	for (SolverGroup d: mgr.getSolverGroups()) {
    		if (s1.equals(d.getSession().getUniqueId())) d1.add(d.getAbbv());
    		if (s2.equals(d.getSession().getUniqueId())) d2.add(d.getAbbv());
    	}
    	return d1.equals(d2);
    }

    /**
     * Load the form
     */
    private void loadForm() throws Exception {
    	sessionContext.checkPermission(id, "TimetableManager", Right.TimetableManagerEdit);
    	
    	form.setUniqueId(id);
    	TimetableManager mgr = TimetableManagerDAO.getInstance().get(id);
        
        form.setEmail(mgr.getEmailAddress());
        form.setExternalId(mgr.getExternalUniqueId());
        
        Set rolesSet = mgr.getManagerRoles();
        ArrayList roles = new ArrayList(rolesSet);
        Collections.sort(roles, new RolesComparator());
        
        for (Iterator i=roles.iterator(); i.hasNext(); ) {
            ManagerRole mgrRole = (ManagerRole) i.next();
            Roles role = mgrRole.getRole();
            form.addToRoles(role);
            if (mgrRole.isPrimary().booleanValue())
                form.setPrimaryRole(role.getRoleId());
            form.getRoleReceiveEmailFlags().add(mgrRole.isReceiveEmails() == null? Boolean.valueOf(false): mgrRole.isReceiveEmails());
        }

        Long sessionId = sessionContext.getUser().getCurrentAcademicSessionId();
        Set<Department> depts = new TreeSet<Department>(mgr.getDepartments());
        for (Department dept: depts) {
            if(dept.getSessionId().equals(sessionId))
                form.addToDepts(dept);
        }        
        Set<SolverGroup> sgs = new TreeSet<SolverGroup>(mgr.getSolverGroups());
        for (SolverGroup sg: sgs) {
            if(sg.getSession().getUniqueId().equals(sessionId))
                form.addToSolverGrs(sg);
        }
        
        if (ApplicationProperty.ManagerExternalIdLookup.isTrue() && ApplicationProperty.ManagerExternalIdLookupClass.value() != null) {
        	form.setLookupEnabled(Boolean.TRUE);
            form.setFirstName(mgr.getFirstName());
            form.setMiddleName(mgr.getMiddleName());
            form.setLastName(mgr.getLastName());
            form.setTitle(mgr.getAcademicTitle());
        } else {
        	form.setLookupEnabled(Boolean.FALSE);
            form.setFirstName(mgr.getFirstName());
            form.setMiddleName(mgr.getMiddleName());
            form.setLastName(mgr.getLastName());
            form.setTitle(mgr.getAcademicTitle());
        }
    }

    /**
     * Add New Manager
     */
    private void addManager() throws Exception {
    	sessionContext.checkPermission(Right.TimetableManagerAdd);
    	
        lookupManager();
        
        TimetableManagerDAO mgrDao = TimetableManagerDAO.getInstance();
        RolesDAO rDao = RolesDAO.getInstance();
        DepartmentDAO dDao = DepartmentDAO.getInstance();
        SolverGroupDAO sgDao = SolverGroupDAO.getInstance();
        
        org.hibernate.Session hibSession = mgrDao.getSession();
        
        Transaction tx = hibSession.beginTransaction();

        TimetableManager mgr = new TimetableManager();
        mgr.setFirstName(form.getFirstName());
        mgr.setMiddleName(form.getMiddleName());
        mgr.setLastName(form.getLastName());
        mgr.setAcademicTitle(form.getTitle());
        mgr.setExternalUniqueId(form.getExternalId());
        mgr.setEmailAddress(form.getEmail());
        
        // Add Roles
        List<Long> roles = form.getRoles();
        List<Boolean> roleReceiveEmails = form.getRoleReceiveEmailFlags();
        Iterator<Boolean> receiveEmailIt = roleReceiveEmails.iterator();
       	for (Iterator<Long> i=roles.iterator(); i.hasNext(); ) {
       	    Roles role = rDao.get(i.next());
       	    ManagerRole mgrRole = new ManagerRole();
       	    mgrRole.setRole(role);
       	    mgrRole.setTimetableManager(mgr);
       	    mgrRole.setPrimary(role.getRoleId().equals(form.getPrimaryRole()));
       	    if (receiveEmailIt.hasNext()){
       	    	mgrRole.setReceiveEmails(receiveEmailIt.next());
       	    } else {
       	    	mgrRole.setReceiveEmails(false);
       	    }
       	    mgr.addToManagerRoles(mgrRole);
       	}        
		hibSession.persist(mgr);

       	// Add departments
		mgr.setDepartments(new HashSet<Department>());
		List<Department> departments = new ArrayList<Department>();
       	for (Iterator<Long> i=form.getDepts().iterator(); i.hasNext(); ) {
       	    Department dept = dDao.get(i.next());
       	    mgr.getDepartments().add(dept);
       	    dept.getTimetableManagers().add(mgr);
    		hibSession.merge(dept);
    		departments.add(dept);
       	}
       	
       	mgr.setSolverGroups(new HashSet<SolverGroup>());
       	List<SolverGroup> solverGroups = new ArrayList<SolverGroup>();
       	for (Iterator<Long> i=form.getSolverGrs().iterator(); i.hasNext(); ) {
       	    SolverGroup sg = sgDao.get(i.next());
       	    mgr.getSolverGroups().add(sg);
       	    sg.getTimetableManagers().add(mgr);
    		hibSession.merge(sg);
    		solverGroups.add(sg);
       	}
       	
       	if (form.getUpdateSessions() != null) {
       		Long currentSessionId = sessionContext.getUser().getCurrentAcademicSessionId();
       		for (Long sessionId: form.getUpdateSessions()) {
       			if (sessionId.equals(currentSessionId)) continue;
       			for (Department dept: departments) {
       	       	    Department other = dept.findSameDepartmentInSession(sessionId);
       	       	    if (other != null) {
       	       	    	mgr.getDepartments().add(other);
       	       	    	other.getTimetableManagers().add(mgr);
       	       	    	hibSession.merge(other);
       	       	    }
       			}
       			for (SolverGroup sg: solverGroups) {
       				SolverGroup other = SolverGroup.findBySessionIdAbbv(sessionId, sg.getAbbv());
       				if (other != null) {
       					mgr.getSolverGroups().add(other);
       					other.getTimetableManagers().add(mgr);
       		    		hibSession.merge(sg);
       				}
       	       	}
       		}
       	}

        ChangeLog.addChange(
                hibSession, 
                sessionContext, 
                mgr, 
                ChangeLog.Source.MANAGER_EDIT, 
                ChangeLog.Operation.CREATE, 
                null, 
                null);
        
       	tx.commit();

       	form.setUniqueId(mgr.getUniqueId());
    }

    /**
     * Update Manager Details
     */
    private void updateManager() throws Exception {
    	sessionContext.checkPermission(form.getUniqueId(), "TimetableManager", Right.TimetableManagerEdit);
    	
        lookupManager();
        
        TimetableManagerDAO mgrDao = TimetableManagerDAO.getInstance();
        RolesDAO rDao = RolesDAO.getInstance();
        DepartmentDAO dDao = DepartmentDAO.getInstance();
        SolverGroupDAO sgDao = SolverGroupDAO.getInstance();
        
        Long sessionId = sessionContext.getUser().getCurrentAcademicSessionId();
        
        org.hibernate.Session hibSession = mgrDao.getSession();
        
        Transaction tx = hibSession.beginTransaction();

        TimetableManager mgr = mgrDao.get(Long.valueOf(form.getUniqueId()));
        mgr.setFirstName(form.getFirstName());
        mgr.setMiddleName(form.getMiddleName());
        mgr.setLastName(form.getLastName());
        mgr.setAcademicTitle(form.getTitle());
        mgr.setExternalUniqueId(form.getExternalId());
        mgr.setEmailAddress(form.getEmail());

        // Update Roles
        List<Long> roles = form.getRoles();
        List<Boolean> roleReceiveEmails = form.getRoleReceiveEmailFlags();
        Set<ManagerRole> mgrRoles = mgr.getManagerRoles();
        if (mgrRoles==null)
            mgrRoles = new HashSet<ManagerRole>();
        
        // Check if roles added or updated
        Iterator<Boolean> receiveEmailIt = roleReceiveEmails.iterator();
       	for (Iterator<Long> i=roles.iterator(); i.hasNext(); ) {
       	    Roles role = rDao.get(i.next());
       	    Boolean receiveEmail = false;
       	    if (receiveEmailIt.hasNext()){
       	    	receiveEmail = receiveEmailIt.next();
       	    }
       	    boolean found = false;
       	    
       	    // Check if role already exists
           	for (Iterator<ManagerRole> j=mgrRoles.iterator(); j.hasNext(); ) {
           	    ManagerRole eMgrRole = j.next();
           	    Roles eRole = eMgrRole.getRole();
           	    
           	    // Exists - check if primary
           	    if (eRole.equals(role)) {
           	    	eMgrRole.setPrimary(role.getRoleId().equals(form.getPrimaryRole()));
               	    eMgrRole.setReceiveEmails(receiveEmail);
               	    found = true;
              	    break;
               	    
           	    }
           	    
           	}       	  
           	
           	// Role does not exist - add  
           	if (!found) {
	       	    ManagerRole mgrRole = new ManagerRole();
	       	    mgrRole.setRole(role);
	       	    mgrRole.setTimetableManager(mgr);
	       	    mgrRole.setPrimary(role.getRoleId().equals(form.getPrimaryRole()));
	       	    mgrRole.setReceiveEmails(receiveEmail);
	       	    mgr.addToManagerRoles(mgrRole);
           	}           	
       	}        

       	// Check if roles deleted
       	for (Iterator<ManagerRole> j=mgrRoles.iterator(); j.hasNext(); ) {
       	    ManagerRole eMgrRole = j.next();
       	    Roles eRole = eMgrRole.getRole();
       	    boolean found = false;

           	for (Iterator<Long> i=roles.iterator(); i.hasNext(); ) {
           	    Roles role = rDao.get(i.next());
           	    if (eRole.equals(role)) {
           	        found = true;
           	        break;
           	    }
           	}
           	
           	if (!found) {
           	    j.remove();
           	}
       	}
       	
       	// Update departments
       	List<Long> depts = form.getDepts();
       	List<Department> departments = new ArrayList<Department>();
       	Set<Department> mgrDepts = mgr.getDepartments();
       	if (mgrDepts==null) {
       	    mgrDepts = new HashSet<Department>();
       	    mgr.setDepartments(mgrDepts);
       	}
       	
        // Check if depts added or updated
       	for (Iterator<Long> i=depts.iterator(); i.hasNext(); ) {
       	    Department dept = dDao.get(i.next());
       	    departments.add(dept);
       	    boolean found = false;
           	for (Iterator<Department> j=mgrDepts.iterator(); j.hasNext(); ) {
           	    Department eDept = j.next();
           	    if (eDept.equals(dept)) {
           	        found = true;
           	        break;
           	    }
           	}
           	
           	if (!found){
           	    mgrDepts.add(dept);
           	    dept.getTimetableManagers().add(mgr);
           	    hibSession.merge(dept);
           	}
       	}

       	// Check if depts deleted
       	for (Iterator<Department> j=mgrDepts.iterator(); j.hasNext(); ) {
       	    Department eDept = j.next();
       	    if (!eDept.getSessionId().equals(sessionId)) continue; //SKIP DEPARTMENTS OF DIFFERENT SESSIONS!!!
       	    boolean found = false;
           	for (Iterator<Long> i=depts.iterator(); i.hasNext(); ) {
           	    Department dept = dDao.get(i.next());
           	    if (eDept.equals(dept)) {
           	        found = true;
           	        break;
           	    }
           	}
           	
           	if (!found) {
           	    j.remove();
           	    eDept.getTimetableManagers().remove(mgr);
           	    hibSession.merge(eDept);
           	}
       	}
       	
       	// Update solver groups
       	List<Long> solverGrs = form.getSolverGrs();
       	List<SolverGroup> solverGroups = new ArrayList<SolverGroup>();
       	Set<SolverGroup> mgrSolverGrs = mgr.getSolverGroups();
       	if (mgrSolverGrs==null) {
       		mgrSolverGrs = new HashSet<SolverGroup>();
       		mgr.setSolverGroups(mgrSolverGrs);
       	}
       	
        // Check if solver group added or updated
       	for (Iterator<Long> i=solverGrs.iterator(); i.hasNext(); ) {
       	    SolverGroup sg = sgDao.get(i.next());
       	    solverGroups.add(sg);
       	    boolean found = false;
           	for (Iterator<SolverGroup> j=mgrSolverGrs.iterator(); j.hasNext(); ) {
           		SolverGroup eSg = j.next();
           	    if (eSg.equals(sg)) {
           	        found = true;
           	        break;
           	    }
           	}
           	
           	if (!found){
           		mgrSolverGrs.add(sg);
           	    sg.getTimetableManagers().add(mgr);
           	    hibSession.merge(sg);
           	}
       	}

       	// Check if depts deleted
       	for (Iterator<SolverGroup> j=mgrSolverGrs.iterator(); j.hasNext(); ) {
       		SolverGroup eSg = j.next();
       	    if (!eSg.getSession().getUniqueId().equals(sessionId)) continue; //SKIP DEPARTMENTS OF DIFFERENT SESSIONS!!!
       	    boolean found = false;
           	for (Iterator<Long> i=solverGrs.iterator(); i.hasNext(); ) {
           		SolverGroup sg = sgDao.get(i.next());
           	    if (eSg.equals(sg)) {
           	        found = true;
           	        break;
           	    }
           	}
           	
           	if (!found) {
           	    j.remove();
           	    eSg.getTimetableManagers().remove(mgr);
           	    hibSession.merge(eSg);
           	}
       	}
       	
       	if (form.getUpdateSessions() != null) {
       		for (Long otherSessionId: form.getUpdateSessions()) {
       			if (otherSessionId.equals(sessionId)) continue;
       			mgrDepts = new HashSet<Department>(mgr.getDepartments());
       			for (Department dept: departments) {
       	       	    Department other = dept.findSameDepartmentInSession(otherSessionId);
       	       	    if (other != null && !mgrDepts.remove(other)) {
       	       	    	mgr.getDepartments().add(other);
       	       	    	other.getTimetableManagers().add(mgr);
       	       	    	hibSession.merge(other);
       	       	    }
       			}
       			for (Department other: mgrDepts) {
       				if (!other.getSessionId().equals(otherSessionId)) continue;
       				mgr.getDepartments().remove(other);
   	       	    	other.getTimetableManagers().remove(mgr);
   	       	    	hibSession.merge(other);
       			}
       			mgrSolverGrs = new HashSet<SolverGroup>(mgr.getSolverGroups());
       			for (SolverGroup sg: solverGroups) {
       				SolverGroup other = SolverGroup.findBySessionIdAbbv(otherSessionId, sg.getAbbv());
       				if (other != null && !mgrSolverGrs.remove(other)) {
       					mgr.getSolverGroups().add(other);
       					other.getTimetableManagers().add(mgr);
       		    		hibSession.merge(other);
       				}
       	       	}
       			for (SolverGroup other: mgrSolverGrs) {
       				if (!other.getSession().getUniqueId().equals(otherSessionId)) continue;
       				mgr.getSolverGroups().remove(other);
   	       	    	other.getTimetableManagers().remove(mgr);
   	       	    	hibSession.merge(other);
       			}
       		}
       	}

       	hibSession.merge(mgr);       	

        ChangeLog.addChange(
                hibSession, 
                sessionContext, 
                mgr, 
                ChangeLog.Source.MANAGER_EDIT, 
                ChangeLog.Operation.UPDATE, 
                null, 
                null);

        tx.commit();
    }

    /**
     * Delete Manager
     * @param request
     * @param form
     */
    private void deleteManager() {
    	sessionContext.checkPermission(form.getUniqueId(), "TimetableManager", Right.TimetableManagerEdit);
        
        TimetableManagerDAO mgrDao = TimetableManagerDAO.getInstance();
        org.hibernate.Session hibSession = mgrDao.getSession();
        TimetableManager mgr = mgrDao.get(form.getUniqueId());

        Transaction tx = hibSession.beginTransaction();
       	
        ChangeLog.addChange(
                hibSession, 
                sessionContext, 
                mgr, 
                ChangeLog.Source.MANAGER_EDIT, 
                ChangeLog.Operation.DELETE, 
                null, 
                null);

       	for (ManagerRole mgrRole: mgr.getManagerRoles()) {
       	    hibSession.remove(mgrRole);
       	}
       	for (Department d: mgr.getDepartments()) {
       		d.getTimetableManagers().remove(mgr);
       		hibSession.merge(d);
       	}
       	for (SolverGroup sg: mgr.getSolverGroups()) {
       		sg.getTimetableManagers().remove(mgr);
       		hibSession.merge(sg);
       	}

        hibSession.remove(mgr);

       	tx.commit();
    }
    
    public String getSession() {
    	return sessionContext.getUser().getCurrentAuthority().getQualifiers("Session").get(0).getQualifierLabel();
    }
}
