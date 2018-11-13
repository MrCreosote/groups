package us.kbase.groups.core.notifications;

import java.util.Collection;

import us.kbase.groups.core.Group;
import us.kbase.groups.core.UserName;
import us.kbase.groups.core.request.GroupRequest;
import us.kbase.groups.core.request.RequestID;

public interface Notifications {

	// TODO JAVADOC
	
	void notify(Collection<UserName> targets, Group group, GroupRequest request);

	void cancel(RequestID requestID);

	void deny(Collection<UserName> targets, GroupRequest request);

	void accept(Collection<UserName> targets, GroupRequest request);
	
}