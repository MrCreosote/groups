package us.kbase.groups.storage.mongo;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bson.Document;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoException;
import com.mongodb.MongoWriteException;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;

import us.kbase.groups.core.Group;
import us.kbase.groups.core.GroupID;
import us.kbase.groups.core.GroupName;
import us.kbase.groups.core.GroupType;
import us.kbase.groups.core.CreateAndModTimes;
import us.kbase.groups.core.CreateModAndExpireTimes;
import us.kbase.groups.core.UserName;
import us.kbase.groups.core.exceptions.GroupExistsException;
import us.kbase.groups.core.exceptions.IllegalParameterException;
import us.kbase.groups.core.exceptions.MissingParameterException;
import us.kbase.groups.core.exceptions.NoSuchGroupException;
import us.kbase.groups.core.exceptions.NoSuchRequestException;
import us.kbase.groups.core.exceptions.RequestExistsException;
import us.kbase.groups.core.request.GroupRequest;
import us.kbase.groups.core.request.GroupRequestStatus;
import us.kbase.groups.core.request.GroupRequestType;
import us.kbase.groups.storage.GroupsStorage;
import us.kbase.groups.storage.exceptions.GroupsStorageException;
import us.kbase.groups.storage.exceptions.StorageInitException;

public class MongoGroupsStorage implements GroupsStorage {
	
	// TODO JAVADOC
	// TODO TEST
	
	/* Don't use mongo built in object mapping to create the returned objects
	 * since that tightly couples the classes to the storage implementation.
	 * Instead, if needed, create classes specific to the implementation for
	 * mapping purposes that produce the returned classes.
	 */
	
	/* Testing the (many) catch blocks for the general mongo exception is pretty hard, since it
	 * appears as though the mongo clients have a heartbeat, so just stopping mongo might trigger
	 * the heartbeat exception rather than the exception you're going for.
	 * 
	 * Mocking the mongo client is probably not the answer:
	 * http://stackoverflow.com/questions/7413985/unit-testing-with-mongodb
	 * https://github.com/mockito/mockito/wiki/How-to-write-good-tests
	 */
	
	private static final int SCHEMA_VERSION = 1;
	
	// collection names
	private static final String COL_CONFIG = "config";
	
	private static final String COL_GROUPS = "groups";
	private static final String COL_REQUESTS = "requests";
	
	private static final Map<String, Map<List<String>, IndexOptions>> INDEXES;
	private static final IndexOptions IDX_UNIQ = new IndexOptions().unique(true);
	private static final IndexOptions IDX_SPARSE = new IndexOptions().sparse(true);
//	private static final IndexOptions IDX_UNIQ_SPARSE =
//			new IndexOptions().unique(true).sparse(true);
	static {
		//hardcoded indexes
		INDEXES = new HashMap<String, Map<List<String>, IndexOptions>>();
		
		// groups indexes
		final Map<List<String>, IndexOptions> groups = new HashMap<>();
		groups.put(Arrays.asList(Fields.GROUP_ID), IDX_UNIQ);
		groups.put(Arrays.asList(Fields.GROUP_OWNER), null);
		INDEXES.put(COL_GROUPS, groups);
		
		
		// requests indexes
		final Map<List<String>, IndexOptions> requests = new HashMap<>();
		// may need compound indexes to speed things up.
		requests.put(Arrays.asList(Fields.REQUEST_ID), IDX_UNIQ);
		requests.put(Arrays.asList(Fields.REQUEST_GROUP_ID), null);
		requests.put(Arrays.asList(Fields.REQUEST_REQUESTER), null);
		requests.put(Arrays.asList(Fields.REQUEST_TARGET), IDX_SPARSE);
		requests.put(Arrays.asList(Fields.REQUEST_CREATION), null);
		requests.put(Arrays.asList(Fields.REQUEST_STATUS), null);
		requests.put(Arrays.asList(Fields.REQUEST_EXPIRATION), null);
		INDEXES.put(COL_REQUESTS, requests);
		
		//config indexes
		final Map<List<String>, IndexOptions> cfg = new HashMap<>();
		//ensure only one config object
		cfg.put(Arrays.asList(Fields.DB_SCHEMA_KEY), IDX_UNIQ);
		INDEXES.put(COL_CONFIG, cfg);
	}
	
	private final MongoDatabase db;
	
	/** Create MongoDB based storage for the Groups application.
	 * @param db the Mongo database the storage system will use.
	 * @throws StorageInitException if the storage system could not be initialized.
	 */
	public MongoGroupsStorage(final MongoDatabase db) 
			throws StorageInitException {
		checkNotNull(db, "db");
		this.db = db;
		ensureIndexes(); // MUST come before check config;
		checkConfig();
	}
	
	
	private void checkConfig() throws StorageInitException  {
		final MongoCollection<Document> col = db.getCollection(COL_CONFIG);
		final Document cfg = new Document(Fields.DB_SCHEMA_KEY, Fields.DB_SCHEMA_VALUE);
		cfg.put(Fields.DB_SCHEMA_UPDATE, false);
		cfg.put(Fields.DB_SCHEMA_VERSION, SCHEMA_VERSION);
		try {
			col.insertOne(cfg);
		} catch (MongoWriteException dk) {
			if (!DuplicateKeyExceptionChecker.isDuplicate(dk)) {
				throw new StorageInitException("There was a problem communicating with the " +
						"database: " + dk.getMessage(), dk);
			}
			// ok, duplicate key means the version doc is already there, this isn't the first
			// startup
			if (col.count() != 1) {
				// if this occurs the indexes are broken, so there's no way to test without
				// altering ensureIndexes()
				throw new StorageInitException(
						"Multiple config objects found in the database. " +
						"This should not happen, something is very wrong.");
			}
			final FindIterable<Document> cur = db.getCollection(COL_CONFIG)
					.find(Filters.eq(Fields.DB_SCHEMA_KEY, Fields.DB_SCHEMA_VALUE));
			final Document doc = cur.first();
			if ((Integer) doc.get(Fields.DB_SCHEMA_VERSION) != SCHEMA_VERSION) {
				throw new StorageInitException(String.format(
						"Incompatible database schema. Server is v%s, DB is v%s",
						SCHEMA_VERSION, doc.get(Fields.DB_SCHEMA_VERSION)));
			}
			if ((Boolean) doc.get(Fields.DB_SCHEMA_UPDATE)) {
				throw new StorageInitException(String.format(
						"The database is in the middle of an update from " +
								"v%s of the schema. Aborting startup.", 
								doc.get(Fields.DB_SCHEMA_VERSION)));
			}
		} catch (MongoException me) {
			throw new StorageInitException(
					"There was a problem communicating with the database: " + me.getMessage(), me);
		}
	}

	private void ensureIndexes() throws StorageInitException {
		for (final String col: INDEXES.keySet()) {
			for (final List<String> idx: INDEXES.get(col).keySet()) {
				final Document index = new Document();
				final IndexOptions opts = INDEXES.get(col).get(idx);
				for (final String field: idx) {
					index.put(field, 1);
				}
				final MongoCollection<Document> dbcol = db.getCollection(col);
				try {
					if (opts == null) {
						dbcol.createIndex(index);
					} else {
						dbcol.createIndex(index, opts);
					}
				} catch (MongoException me) {
					throw new StorageInitException(
							"Failed to create index: " + me.getMessage(), me);
				}
			}
		}
	}

	private static class DuplicateKeyExceptionChecker {
		
		// might need this stuff later, so keeping for now.
		
		/*
		// super hacky and fragile, but doesn't seem another way to do this.
		private final Pattern keyPattern = Pattern.compile("dup key:\\s+\\{ : \"(.*)\" \\}");
		private final Pattern indexPattern = Pattern.compile(
				"duplicate key error (index|collection): " +
				"\\w+\\.(\\w+)( index: |\\.\\$)([\\.\\w]+)\\s+");
		
		private final boolean isDuplicate;
		private final Optional<String> collection;
		private final Optional<String> index;
		private final Optional<String> key;
		
		public DuplicateKeyExceptionChecker(final MongoWriteException mwe)
				throws AssemblyHomologyStorageException {
			// split up indexes better at some point - e.g. in a Document
			isDuplicate = isDuplicate(mwe);
			if (isDuplicate) {
				final Matcher indexMatcher = indexPattern.matcher(mwe.getMessage());
				if (indexMatcher.find()) {
					collection = Optional.of(indexMatcher.group(2));
					index = Optional.of(indexMatcher.group(4));
				} else {
					throw new AssemblyHomologyStorageException(
							"Unable to parse duplicate key error: " +
							// could include a token hash as the key, so split it out if it's there
							mwe.getMessage().split("dup key")[0], mwe);
				}
				final Matcher keyMatcher = keyPattern.matcher(mwe.getMessage());
				if (keyMatcher.find()) {
					key = Optional.of(keyMatcher.group(1));
				} else { // some errors include the dup key, some don't
					key = Optional.absent();
				}
			} else {
				collection = Optional.absent();
				index = Optional.absent();
				key = Optional.absent();
			}
			
		} */
		
		public static boolean isDuplicate(final MongoWriteException mwe) {
			return mwe.getError().getCategory().equals(ErrorCategory.DUPLICATE_KEY);
		}
		
		/*
		public boolean isDuplicate() {
			return isDuplicate;
		}

		public Optional<String> getCollection() {
			return collection;
		}

		public Optional<String> getIndex() {
			return index;
		}

		@SuppressWarnings("unused") // may need later
		public Optional<String> getKey() {
			return key;
		}
		*/
	}

	@Override
	public void createGroup(final Group group)
			throws GroupExistsException, GroupsStorageException {
		checkNotNull(group, "group");
		final Document u = new Document(
				Fields.GROUP_ID, group.getGroupID().getName())
				.append(Fields.GROUP_NAME, group.getGroupName().getName())
				.append(Fields.GROUP_OWNER, group.getOwner().getName())
				.append(Fields.GROUP_TYPE, group.getType().toString())
				.append(Fields.GROUP_CREATION, Date.from(group.getCreationDate()))
				.append(Fields.GROUP_MODIFICATION, Date.from(group.getModificationDate()))
				.append(Fields.GROUP_DESCRIPTION, group.getDescription().orNull());
		try {
			db.getCollection(COL_GROUPS).insertOne(u);
		} catch (MongoWriteException mwe) {
			// not happy about this, but getDetails() returns an empty map
			if (DuplicateKeyExceptionChecker.isDuplicate(mwe)) {
				throw new GroupExistsException(group.getGroupID().getName());
			} else {
				throw new GroupsStorageException("Database write failed", mwe);
			}
		} catch (MongoException e) {
			throw new GroupsStorageException("Connection to database failed: " +
					e.getMessage(), e);
		}
	}
	
	@Override
	public Group getGroup(final GroupID groupID)
			throws GroupsStorageException, NoSuchGroupException {
		checkNotNull(groupID, "groupID");
		final Document grp = findOne(
				COL_GROUPS, new Document(Fields.GROUP_ID, groupID.getName()));
		if (grp == null) {
			throw new NoSuchGroupException(groupID.getName());
		} else {
			return toGroup(grp);
		}
	}
	
	@Override
	public List<Group> getGroups() throws GroupsStorageException {
		final List<Group> ret = new LinkedList<>();
		try {
			final FindIterable<Document> gdocs = db.getCollection(COL_GROUPS)
					.find().sort(new Document(Fields.GROUP_ID, 1));
			for (final Document gdoc: gdocs) {
				ret.add(toGroup(gdoc));
			}
		} catch (MongoException e) {
			throw new GroupsStorageException(
					"Connection to database failed: " + e.getMessage(), e);
		}
		return ret;
	}
	
	private Group toGroup(final Document grp) throws GroupsStorageException {
		try {
			return Group.getBuilder(
					new GroupID(grp.getString(Fields.GROUP_ID)),
					new GroupName(grp.getString(Fields.GROUP_NAME)),
					new UserName(grp.getString(Fields.GROUP_OWNER)),
					new CreateAndModTimes(
							grp.getDate(Fields.GROUP_CREATION).toInstant(),
							grp.getDate(Fields.GROUP_MODIFICATION).toInstant()))
					// TODO NOW check valueOf error conditions 
					.withType(GroupType.valueOf(grp.getString(Fields.GROUP_TYPE)))
					.withDescription(grp.getString(Fields.GROUP_DESCRIPTION))
					.build();
		} catch (MissingParameterException | IllegalParameterException e) {
			throw new GroupsStorageException(
					"Unexpected value in database: " + e.getMessage(), e);
		}
	}
	
	@Override
	public void storeRequest(final GroupRequest request)
			throws RequestExistsException, GroupsStorageException {
		checkNotNull(request, "request");
		final Document u = new Document(
				Fields.REQUEST_ID, request.getID().toString())
				.append(Fields.REQUEST_GROUP_ID, request.getGroupID().getName())
				.append(Fields.REQUEST_REQUESTER, request.getRequester().getName())
				.append(Fields.REQUEST_STATUS, request.getStatus().toString())
				.append(Fields.REQUEST_TYPE, request.getType().toString())
				.append(Fields.REQUEST_TARGET, request.getTarget().isPresent() ?
						request.getTarget().get().getName() : null)
				.append(Fields.REQUEST_CREATION, Date.from(request.getCreationDate()))
				.append(Fields.REQUEST_MODIFICATION, Date.from(request.getModificationDate()))
				.append(Fields.REQUEST_EXPIRATION, Date.from(request.getExpirationDate()));
		try {
			db.getCollection(COL_REQUESTS).insertOne(u);
		} catch (MongoWriteException mwe) {
			// not happy about this, but getDetails() returns an empty map
			if (DuplicateKeyExceptionChecker.isDuplicate(mwe)) {
				throw new RequestExistsException(request.getID().toString());
			} else {
				throw new GroupsStorageException("Database write failed", mwe);
			}
		} catch (MongoException e) {
			throw new GroupsStorageException("Connection to database failed: " +
					e.getMessage(), e);
		}
	}
	
	@Override
	public GroupRequest getRequest(final UUID requestID)
			throws NoSuchRequestException, GroupsStorageException {
		checkNotNull(requestID, "requestID");
		final Document req = findOne(
				COL_REQUESTS, new Document(Fields.REQUEST_ID, requestID.toString()));
		if (req == null) {
			throw new NoSuchRequestException(requestID.toString());
		} else {
			return toRequest(req);
		}
	}
	
	private GroupRequest toRequest(final Document req) throws GroupsStorageException {
		try {
			final String target = req.getString(Fields.REQUEST_TARGET);
			return GroupRequest.getBuilder(
					UUID.fromString(req.getString(Fields.REQUEST_ID)),
					new GroupID(req.getString(Fields.REQUEST_GROUP_ID)),
					new UserName(req.getString(Fields.REQUEST_REQUESTER)),
					CreateModAndExpireTimes.getBuilder(
							req.getDate(Fields.REQUEST_CREATION).toInstant(),
							req.getDate(Fields.REQUEST_EXPIRATION).toInstant())
							.withModificationTime(req.getDate(Fields.REQUEST_MODIFICATION)
									.toInstant())
							.build())
					.withType(
							GroupRequestType.valueOf(req.getString(Fields.REQUEST_TYPE)),
							target == null ? null : new UserName(target))
					.withStatus(GroupRequestStatus.valueOf(req.getString(Fields.REQUEST_STATUS)))
					.build();
		} catch (IllegalParameterException | MissingParameterException |
				IllegalArgumentException e) {
			throw new GroupsStorageException(
					"Unexpected value in database: " + e.getMessage(), e);
		}
	}

	/* Use this for finding documents where indexes should force only a single
	 * document. Assumes the indexes are doing their job.
	 */
	private Document findOne(
			final String collection,
			final Document query)
			throws GroupsStorageException {
		return findOne(collection, query, null);
	}
	
	/* Use this for finding documents where indexes should force only a single
	 * document. Assumes the indexes are doing their job.
	 */
	private Document findOne(
			final String collection,
			final Document query,
			final Document projection)
			throws GroupsStorageException {
		try {
			return db.getCollection(collection).find(query).projection(projection).first();
		} catch (MongoException e) {
			throw new GroupsStorageException(
					"Connection to database failed: " + e.getMessage(), e);
		}
	}
}
