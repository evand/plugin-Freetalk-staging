/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.WoT;

import plugins.Freetalk.FTIdentity;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.Persistent;
import plugins.Freetalk.Persistent.Indexed;
import plugins.Freetalk.exceptions.InvalidParameterException;
import freenet.keys.FreenetURI;
import freenet.support.Base64;
import freenet.support.CurrentTimeUTC;
import freenet.support.Logger;
import freenet.support.StringValidityChecker;


/**
 * 
 * Activation policy: WoTIdentity does automatic activation on its own.
 * This means that WoTIdentity can be activated to a depth of only 1 when querying them from the database.
 * All methods automatically activate the object to any needed higher depth.
 * 
 * TODO: Change all code which queries for identities to use the lowest possible activation depth to benefit from automatic activation.
 * 
 * @author xor (xor@freenetproject.org)
 */
@Indexed // TODO: Check whether we really need this index.
public class WoTIdentity extends Persistent implements FTIdentity {
	
	/* Attributes, stored in the database. */
	
	@Indexed
	private final String mID;
    
	/** The requestURI used to fetch this identity from Freenet */
	private final FreenetURI mRequestURI;
	
	/** The nickname of this Identity */
	private final String mNickname;
	
	/**
	 * Used for garbage collecting old identities which are not returned by the WoT plugin anymore.
	 * We delete them if they were not received for a certain time interval.
	 */
	private long mLastReceivedFromWoT;


	public WoTIdentity(String myID, FreenetURI myRequestURI, String myNickname) {
		if(myID == null) throw new IllegalArgumentException("ID == null");
		if(myID.length() == 0) throw new IllegalArgumentException("ID.length() == 0");
		if(myRequestURI == null) throw new IllegalArgumentException("RequestURI == null");
		if(myNickname == null) throw new IllegalArgumentException("Nickname == null");
		
		try {
			validateNickname(myNickname);
		} catch (InvalidParameterException e) {
			throw new IllegalArgumentException(e);
		}
		
		mID = myID;
		mRequestURI = myRequestURI;
		mNickname = myNickname;
		mLastReceivedFromWoT = CurrentTimeUTC.getInMillis();
	}

	public String getID() {
		// activate(1);	// 1 is the default activation depth, no need to execute activate(1).
		return mID;
	}
	
	/**
	 * Generates a unique id from a {@link FreenetURI}. 
	 * It is simply a String representing it's routing key.
	 * We use this to identify identities and perform requests on the database. 
	 * 
	 * @param uri The requestURI of the Identity
	 * @return A string to uniquely identify an Identity
	 */
	public static String getIDFromURI (FreenetURI uri) {
		/* WARNING: This is a copy of the code of plugins.WoT.Identity. Freetalk is not allowed to have its own custom IDs, you cannot change
		 * this code here. */
		return Base64.encode(uri.getRoutingKey());
	}

	public FreenetURI getRequestURI() {
		checkedActivate(3); // String[] is no nested object to db4o so 3 is sufficient.
		return mRequestURI;
	}

	public String getNickname() {
		// activate(1);	// 1 is the default activation depth, no need to execute activate(1)
		return mNickname;
	}

	protected String getNickname(int maxLength) {
		// activate(1);	// 1 is the default activation depth, no need to execute activate(1)
		if(mNickname.length() > maxLength) {
			return mNickname.substring(0, maxLength) + "...";
		}
		return mNickname;
	}

	public String getShortestUniqueName() {
		return mFreetalk.getIdentityManager().getShortestUniqueName(this);
	}

	public String getFreetalkAddress() {
		// activate(1);	// 1 is the default activation depth, no need to execute activate(1)
		return mNickname + "@" + mID + "." + Freetalk.WOT_CONTEXT.toLowerCase();	
	}
	
	/**
	 * Returns a Freetalk-address with a maximal content length.
	 * The format will be "nickname@abbreviated_routing_key...", i.e. 3 dots will be appended if the length exceeds the maximal length.
	 * The "@" and "..." are not included in the length computation - therefore its called maximal <b>content</b> length, not maximal length.
	 * If the nickname does not fit in the maximal length it is NOT abbreviated, the full nickname is returned then.
	 * 
	 * The reason for this weird definition is to allow easy computation of nicknames which have a shortest unique length...
	 * See {@link WoTIdentityManager.updateShortestUniqueNicknameCache} for how this is used.
	 */
	protected String getFreetalkAddress(int maxContentLength) {
		final String address = getFreetalkAddress();
		
		if(getNickname().length() > maxContentLength)
			return getNickname();
		
		if(address.length() > maxContentLength) {
			return address.substring(0, maxContentLength+1) + "..."; // "+1" because the "@" does not count as length.
		}
		return address;
	}

	public synchronized long getLastReceivedFromWoT() {
		// activate(1);	// 1 is the default activation depth, no need to execute activate(1)
		return mLastReceivedFromWoT;
	}
	
	/**
	 * Set the time this identity was last received from the WoT plugin to the given UTC time in milliseconds
	 * @param time
	 */
	public synchronized void setLastReceivedFromWoT(long time) {
		mLastReceivedFromWoT = time;
		storeWithoutCommit(); // TODO: Move store() calls outside of class identity
	}

	/**
	 * Validates the nickname. If it is valid, nothing happens. If it is invalid, an exception is thrown which exactly describes what is
	 * wrong about the nickname.
	 * 
	 * @throws InvalidParameterException If the nickname is invalid, the exception contains a description of the problem as message. 
	 */
	/* IMPORTANT: This code is duplicated in plugins.WoT.Identity.isNicknameValid().
	 * Please also modify it there if you modify it here */
	// FIXME: Reconsider the length limit of 50 characters. Maybe 20 or 30 would be enough??
	// IMHO the limit should be sufficiently short to allow the UI to always display unshortened nicknames: If we shorten nicknames, the caching of 
	// shortest unique nicknames gets too complicated because we need multiple caches for different lengths.
	public static void validateNickname(String newNickname) throws InvalidParameterException {
		if(!StringValidityChecker.containsNoIDNBlacklistCharacters(newNickname)
		|| !StringValidityChecker.containsNoInvalidCharacters(newNickname)
		|| !StringValidityChecker.containsNoLinebreaks(newNickname)
		|| !StringValidityChecker.containsNoControlCharacters(newNickname)
		|| !StringValidityChecker.containsNoInvalidFormatting(newNickname))
			throw new InvalidParameterException("Nickname contains invalid characters"); /* TODO: Tell the user which ones are invalid!!! */
		
		if(newNickname.length() == 0) throw new InvalidParameterException("Blank nickname.");
		if(newNickname.length() > 50) throw new InvalidParameterException("Nickname is too long, the limit is 50 characters.");
	}

	protected void checkedCommit(Object loggingObject) {
		super.checkedCommit(loggingObject);
	}
	
	/**
	 * You have to synchronize on this object before modifying the identity and calling storeAndCommit. 
	 */
	public void storeAndCommit() {
		synchronized(mDB.lock()) {
			storeWithoutCommit();
			checkedCommit(this);
		}
	}

	protected void storeWithoutCommit() {
		try {		
			// 3 is the maximal depth of all getter functions. You have to adjust this when introducing new member variables.
			checkedActivate(3);

			// You have to take care to keep the list of stored objects synchronized with those being deleted in deleteWithoutCommit() !
			
			checkedStore(mRequestURI);
			checkedStore();
		}
		catch(RuntimeException e) {
			checkedRollbackAndThrow(e);
		}
	}
	
	protected void deleteWithoutCommit() {
		try {
			// 3 is the maximal depth of all getter functions. You have to adjust this when introducing new member variables.
			checkedActivate(this, 3);
			
			checkedDelete();
			
			mRequestURI.removeFrom(mDB);
		}
		catch(RuntimeException e) {
			checkedRollbackAndThrow(e);
		}
	}

	public String toString() {
		if(mDB != null)
			return getFreetalkAddress();
		
		// We do not throw a NPE because toString() is usually used in logging, we want the logging to be robust
		
		Logger.error(this, "toString() called before initializeTransient()!");
		
		return super.toString() + " (intializeTransient() not called!, identity ID may be null, here it is: " + mID + ")";
	}
	
}
