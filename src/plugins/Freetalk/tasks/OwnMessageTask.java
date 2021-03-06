/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.tasks;

import plugins.Freetalk.FTOwnIdentity;

/**
 * An OwnMessageTask is a task which is processed not only when it's processing time is due but also when it's owner posts a new message.
 */
// @Indexed // I can't think of any query which would need to get all OwnMessageTask objects.
public abstract class OwnMessageTask extends PersistentTask {

	protected OwnMessageTask(FTOwnIdentity myOwner) {
		super(myOwner);
	}

}
