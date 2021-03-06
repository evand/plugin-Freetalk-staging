/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.WoT;

import plugins.Freetalk.FTIdentity;
import plugins.Freetalk.FTOwnIdentity;
import plugins.Freetalk.Persistent.Indexed;
import plugins.Freetalk.exceptions.NotInTrustTreeException;
import plugins.Freetalk.exceptions.NotTrustedException;
import freenet.keys.FreenetURI;

/**
 * 
 * 
 * Activation policy: WoTOwnIdentity does automatic activation on its own.
 * This means that WoTOwnIdentity can be activated to a depth of only 1 when querying them from the database.
 * All methods automatically activate the object to any needed higher depth.
 * 
 * TODO: Change all code which queries for identities to use the lowest possible activation depth to benefit from automatic activation.
 * 
 * @author xor (xor@freenetproject.org)
 */
@Indexed
public final class WoTOwnIdentity extends WoTIdentity implements FTOwnIdentity {
	
	/* Attributes, stored in the database. */

	private final FreenetURI mInsertURI;

    /** If true then auto-subscribe to boards that were subscribed in the NNTP client */
    private boolean mNntpAutoSubscribeBoards;


	public WoTOwnIdentity(String myID, FreenetURI myRequestURI, FreenetURI myInsertURI, String myNickname) {
		super(myID, myRequestURI, myNickname);
		if(myInsertURI == null)
			throw new IllegalArgumentException();
		mInsertURI = myInsertURI;
	}

	public FreenetURI getInsertURI() {
		checkedActivate(3); // String[] is no nested object to db4o so 3 is sufficient.
		return mInsertURI;
	}

	public boolean wantsMessagesFrom(FTIdentity identity) throws Exception {
		if(!(identity instanceof WoTIdentity))
			throw new IllegalArgumentException();
		
		try {
			// TODO: Investigate whether we could make the lower limit configurable. It would require us not to delete the identities if the configurated limit is
			// below zero. That would involve chaning WoT though. Or we could only allow positive limits.
			return getScoreFor((WoTIdentity)identity) >= 0;
		}
		catch(NotInTrustTreeException e) {
			return false;
		}
	}

	public int getScoreFor(WoTIdentity identity) throws NotInTrustTreeException, Exception {
		return mFreetalk.getIdentityManager().getScore(this, identity);
	}

	public int getTrustIn(WoTIdentity identity) throws NotTrustedException, Exception {
		return mFreetalk.getIdentityManager().getTrust(this, identity);
	}

	public void setTrust(WoTIdentity identity, int trust, String comment) throws Exception {
		mFreetalk.getIdentityManager().setTrust(this, identity, trust, comment);
	}
	
    /**
     * Checks whether this Identity auto-subscribes to boards subscribed in NNTP client.
     * 
     * @return Whether this Identity auto-subscribes to boards subscribed in NNTP client or not.
     */
    public boolean nntpAutoSubscribeBoards() {
        return mNntpAutoSubscribeBoards;
    }
    
    /**
     * Sets if this Identity auto-subscribes to boards subscribed in NNTP client. 
     */
    public void setNntpAutoSubscribeBoards(boolean nntpAutoSubscribeBoards) {
        mNntpAutoSubscribeBoards = nntpAutoSubscribeBoards;
    }
    
	public void storeWithoutCommit() {
		try {
			// 3 is the maximal depth of all getter functions. You have to adjust this when changing the set of member variables.
			checkedActivate(3);
			
			// You have to take care to keep the list of stored objects synchronized with those being deleted in deleteWithoutCommit() !

			checkedStore(mInsertURI);
			checkedStore();
		}
		catch(RuntimeException e) {
			checkedRollbackAndThrow(e);
		}
	}

	protected void deleteWithoutCommit() {	
		try {
			// super.deleteWithoutCommit() does the following already so there is no need to do it here
			// 3 is the maximal depth of all getter functions. You have to adjust this when changing the set of member variables.
			// DBUtil.checkedActivate(db, this, 3);
			
			super.deleteWithoutCommit();
			
			mInsertURI.removeFrom(mDB);
		}
		catch(RuntimeException e) {
			checkedRollbackAndThrow(e);
		}
	}
}
