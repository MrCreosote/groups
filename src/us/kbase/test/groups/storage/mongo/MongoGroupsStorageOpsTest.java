package us.kbase.test.groups.storage.mongo;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.bson.Document;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import ch.qos.logback.classic.spi.ILoggingEvent;
import us.kbase.groups.core.Group;
import us.kbase.groups.core.GroupID;
import us.kbase.groups.core.GroupName;
import us.kbase.groups.core.GroupType;
import us.kbase.groups.core.CreateAndModTimes;
import us.kbase.groups.core.CreateModAndExpireTimes;
import us.kbase.groups.core.UserName;
import us.kbase.groups.core.exceptions.GroupExistsException;
import us.kbase.groups.core.exceptions.NoSuchGroupException;
import us.kbase.groups.core.exceptions.NoSuchUserException;
import us.kbase.groups.core.exceptions.RequestExistsException;
import us.kbase.groups.core.exceptions.UserIsMemberException;
import us.kbase.groups.core.request.GroupRequest;
import us.kbase.groups.core.request.GroupRequestStatus;
import us.kbase.groups.core.request.GroupRequestStatusType;
import us.kbase.groups.core.request.RequestID;
import us.kbase.groups.storage.exceptions.GroupsStorageException;
import us.kbase.test.groups.MongoStorageTestManager;
import us.kbase.test.groups.TestCommon;

public class MongoGroupsStorageOpsTest {

	private static MongoStorageTestManager manager;
	private static List<ILoggingEvent> logEvents;
	private static Path TEMP_DIR;
	private static Path EMPTY_FILE_MSH;
	
	@BeforeClass
	public static void setUp() throws Exception {
		logEvents = TestCommon.setUpSLF4JTestLoggerAppender("us.kbase.assemblyhomology");
		manager = new MongoStorageTestManager("test_mongoahstorage");
		TEMP_DIR = TestCommon.getTempDir().resolve("StorageTest_" + UUID.randomUUID().toString());
		Files.createDirectories(TEMP_DIR);
		EMPTY_FILE_MSH = TEMP_DIR.resolve(UUID.randomUUID().toString() + ".msh");
		Files.createFile(EMPTY_FILE_MSH);
	}
	
	@AfterClass
	public static void tearDownClass() throws Exception {
		if (manager != null) {
			manager.destroy();
		}
		final boolean deleteTempFiles = TestCommon.isDeleteTempFiles();
		if (TEMP_DIR != null && Files.exists(TEMP_DIR) && deleteTempFiles) {
			FileUtils.deleteQuietly(TEMP_DIR.toFile());
		}
	}
	
	@Before
	public void before() throws Exception {
		manager.reset();
		logEvents.clear();
	}
	
	// TODO TEST add more tests for create and get group /request
	
	@Test
	public void createAndGetGroupMinimal() throws Exception {
		manager.storage.createGroup(Group.getBuilder(
				new GroupID("gid"), new GroupName("name"), new UserName("uname"),
				new CreateAndModTimes(Instant.ofEpochMilli(20000), Instant.ofEpochMilli(30000)))
				.build());
		
		assertThat("incorrect group", manager.storage.getGroup(new GroupID("gid")),
				is(Group.getBuilder(
						new GroupID("gid"), new GroupName("name"), new UserName("uname"),
						new CreateAndModTimes(
								Instant.ofEpochMilli(20000), Instant.ofEpochMilli(30000)))
						.build()));
	}
	
	@Test
	public void createAndGetGroupMaximal() throws Exception {
		manager.storage.createGroup(Group.getBuilder(
				new GroupID("gid"), new GroupName("name"), new UserName("uname"),
				new CreateAndModTimes(Instant.ofEpochMilli(20000), Instant.ofEpochMilli(30000)))
				.withType(GroupType.PROJECT)
				.withDescription("desc")
				.withMember(new UserName("foo"))
				.withMember(new UserName("bar"))
				.build());
		
		assertThat("incorrect group", manager.storage.getGroup(new GroupID("gid")),
				is(Group.getBuilder(
						new GroupID("gid"), new GroupName("name"), new UserName("uname"),
						new CreateAndModTimes(
								Instant.ofEpochMilli(20000), Instant.ofEpochMilli(30000)))
						.withType(GroupType.PROJECT)
						.withDescription("desc")
						.withMember(new UserName("foo"))
						.withMember(new UserName("bar"))
						.build()));
	}
	
	@Test
	public void createGroupFail() throws Exception {
		failCreateGroup(null, new NullPointerException("group"));
		
		manager.storage.createGroup(Group.getBuilder(
				new GroupID("gid"), new GroupName("name"), new UserName("uname"),
				new CreateAndModTimes(Instant.ofEpochMilli(20000), Instant.ofEpochMilli(30000)))
				.build());
		
		failCreateGroup(Group.getBuilder(
				new GroupID("gid"), new GroupName("name1"), new UserName("uname1"),
				new CreateAndModTimes(Instant.ofEpochMilli(21000), Instant.ofEpochMilli(31000)))
				.build(),
				new GroupExistsException("gid"));
	}
	
	private void failCreateGroup(final Group g, final Exception expected) {
		try {
			manager.storage.createGroup(g);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
	}
	
	@Test
	public void getGroupFail() throws Exception {
		failGetGroup(null, new NullPointerException("groupID"));
		
		manager.storage.createGroup(Group.getBuilder(
				new GroupID("gid"), new GroupName("name"), new UserName("uname"),
				new CreateAndModTimes(Instant.ofEpochMilli(20000), Instant.ofEpochMilli(30000)))
				.build());
		failGetGroup(new GroupID("gid1"), new NoSuchGroupException("gid1"));
	}
	
	private void failGetGroup(final GroupID id, final Exception expected) {
		try {
			manager.storage.getGroup(id);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
	}
	
	@Test
	public void illegalGroupDataInDB() throws Exception {
		manager.storage.createGroup(Group.getBuilder(
				new GroupID("gid"), new GroupName("name"), new UserName("uname"),
				new CreateAndModTimes(Instant.ofEpochMilli(20000), Instant.ofEpochMilli(30000)))
				.build());
		manager.db.getCollection("groups").updateOne(new Document("id", "gid"),
				new Document("$set", new Document("name", "")));
		
		failGetGroup(new GroupID("gid"), new GroupsStorageException(
				"Unexpected value in database: 30000 Missing input parameter: group name"));
		
		manager.db.getCollection("groups").updateOne(new Document("id", "gid"),
				new Document("$set", new Document("name", "foo").append("own", "a*b")));
	
		failGetGroup(new GroupID("gid"), new GroupsStorageException(
				"Unexpected value in database: 30010 Illegal user name: " +
				"Illegal character in user name a*b: *"));
		
		manager.db.getCollection("groups").updateOne(new Document("id", "gid"),
				new Document("$set", new Document("own", "a").append("type", "Teem")));
	
		failGetGroup(new GroupID("gid"), new GroupsStorageException(
				"Unexpected value in database: No enum constant " +
				"us.kbase.groups.core.GroupType.Teem"));
	}
	
	@Test
	public void getGroups() throws Exception {
		manager.storage.createGroup(Group.getBuilder(
				new GroupID("gid"), new GroupName("name3"), new UserName("uname3"),
				new CreateAndModTimes(Instant.ofEpochMilli(40000), Instant.ofEpochMilli(50000)))
				.build());
		
		manager.storage.createGroup(Group.getBuilder(
				new GroupID("aid"), new GroupName("name1"), new UserName("uname1"),
				new CreateAndModTimes(Instant.ofEpochMilli(10000), Instant.ofEpochMilli(10000)))
				.withType(GroupType.PROJECT)
				.withDescription("desc1")
				.withMember(new UserName("foo1"))
				.withMember(new UserName("bar1"))
				.build());
		
		manager.storage.createGroup(Group.getBuilder(
				new GroupID("fid"), new GroupName("name2"), new UserName("uname2"),
				new CreateAndModTimes(Instant.ofEpochMilli(20000), Instant.ofEpochMilli(30000)))
				.withType(GroupType.TEAM)
				.withDescription("desc2")
				.withMember(new UserName("foo2"))
				.build());
		
		assertThat("incorrect get group", manager.storage.getGroups(), is(Arrays.asList(
				Group.getBuilder(
						new GroupID("aid"), new GroupName("name1"), new UserName("uname1"),
						new CreateAndModTimes(Instant.ofEpochMilli(10000),
								Instant.ofEpochMilli(10000)))
						.withType(GroupType.PROJECT)
						.withDescription("desc1")
						.withMember(new UserName("foo1"))
						.withMember(new UserName("bar1"))
						.build(),
				Group.getBuilder(
						new GroupID("fid"), new GroupName("name2"), new UserName("uname2"),
						new CreateAndModTimes(Instant.ofEpochMilli(20000),
								Instant.ofEpochMilli(30000)))
						.withType(GroupType.TEAM)
						.withDescription("desc2")
						.withMember(new UserName("foo2"))
						.build(),
				Group.getBuilder(
						new GroupID("gid"), new GroupName("name3"), new UserName("uname3"),
						new CreateAndModTimes(Instant.ofEpochMilli(40000),
								Instant.ofEpochMilli(50000)))
						.build()
				)));
	}
	
	@Test
	public void getGroupsEmpty() throws Exception {
		assertThat("incorrect get groups", manager.storage.getGroups(),
				is(Collections.emptyList()));
	}
	
	@Test
	public void addMember() throws Exception {
		manager.storage.createGroup(Group.getBuilder(
				new GroupID("gid"), new GroupName("name3"), new UserName("uname3"),
				new CreateAndModTimes(Instant.ofEpochMilli(40000), Instant.ofEpochMilli(50000)))
				.build());
		
		manager.storage.addMember(new GroupID("gid"), new UserName("foo"));
		manager.storage.addMember(new GroupID("gid"), new UserName("bar"));
		
		assertThat("incorrect add member result", manager.storage.getGroup(new GroupID("gid")),
				is(Group.getBuilder(
						new GroupID("gid"), new GroupName("name3"), new UserName("uname3"),
						new CreateAndModTimes(Instant.ofEpochMilli(40000),
								Instant.ofEpochMilli(50000)))
						.withMember(new UserName("foo"))
						.withMember(new UserName("bar"))
						.build()));
	}
	
	@Test
	public void addMemberFailNulls() throws Exception {
		failAddMember(null, new UserName("f"), new NullPointerException("groupID"));
		failAddMember(new GroupID("g"), null, new NullPointerException("member"));
	}
	
	@Test
	public void addMemberFailNoSuchGroup() throws Exception {
		manager.storage.createGroup(Group.getBuilder(
				new GroupID("gid"), new GroupName("name3"), new UserName("uname3"),
				new CreateAndModTimes(Instant.ofEpochMilli(40000), Instant.ofEpochMilli(50000)))
				.build());
		
		failAddMember(new GroupID("gid1"), new UserName("foo"), new NoSuchGroupException("gid1"));
	}
	
	@Test
	public void addMemberFailExists() throws Exception {
		// add test for admin fail when admins are supported
		manager.storage.createGroup(Group.getBuilder(
				new GroupID("gid"), new GroupName("name3"), new UserName("uname3"),
				new CreateAndModTimes(Instant.ofEpochMilli(40000), Instant.ofEpochMilli(50000)))
				.withMember(new UserName("foo"))
				.build());
		
		failAddMember(new GroupID("gid"), new UserName("foo"),
				new UserIsMemberException("User foo is already a member of group gid"));
	}
	
	@Test
	public void addMemberFailOwner() throws Exception {
		// add test for admin fail when admins are supported
		manager.storage.createGroup(Group.getBuilder(
				new GroupID("gid"), new GroupName("name3"), new UserName("uname3"),
				new CreateAndModTimes(Instant.ofEpochMilli(40000), Instant.ofEpochMilli(50000)))
				.build());
		
		failAddMember(new GroupID("gid"), new UserName("uname3"),
				new UserIsMemberException("User uname3 is the owner of group gid"));
		
	}
	
	private void failAddMember(
			final GroupID gid,
			final UserName member,
			final Exception expected) {
		try {
			manager.storage.addMember(gid, member);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
	}
	
	@Test
	public void removeMember() throws Exception {
		manager.storage.createGroup(Group.getBuilder(
				new GroupID("gid"), new GroupName("name3"), new UserName("uname3"),
				new CreateAndModTimes(Instant.ofEpochMilli(40000), Instant.ofEpochMilli(50000)))
				.withMember(new UserName("foo"))
				.withMember(new UserName("bar"))
				.withMember(new UserName("baz"))
				.build());
		
		manager.storage.removeMember(new GroupID("gid"), new UserName("foo"));
		manager.storage.removeMember(new GroupID("gid"), new UserName("baz"));
		
		assertThat("incorrect group", manager.storage.getGroup(new GroupID("gid")), is(
				Group.getBuilder(
						new GroupID("gid"), new GroupName("name3"), new UserName("uname3"),
						new CreateAndModTimes(Instant.ofEpochMilli(40000),
								Instant.ofEpochMilli(50000)))
						.withMember(new UserName("bar"))
						.build()));
	}
	
	@Test
	public void removeMemberFailNulls() throws Exception {
		failRemoveMember(null, new UserName("f"), new NullPointerException("groupID"));
		failRemoveMember(new GroupID("g"), null, new NullPointerException("member"));
	}
	
	@Test
	public void removeMemberFailNoSuchGroup() throws Exception {
		manager.storage.createGroup(Group.getBuilder(
				new GroupID("gid"), new GroupName("name3"), new UserName("uname3"),
				new CreateAndModTimes(Instant.ofEpochMilli(40000), Instant.ofEpochMilli(50000)))
				.build());
		
		failRemoveMember(new GroupID("gid1"), new UserName("foo"),
				new NoSuchGroupException("gid1"));
	}
	
	@Test
	public void removeMemberFailNoSuchUser() throws Exception {
		manager.storage.createGroup(Group.getBuilder(
				new GroupID("gid"), new GroupName("name3"), new UserName("uname3"),
				new CreateAndModTimes(Instant.ofEpochMilli(40000), Instant.ofEpochMilli(50000)))
				.withMember(new UserName("foo"))
				.build());
		
		failRemoveMember(new GroupID("gid"), new UserName("bar"), new NoSuchUserException(
				"No member bar in group gid"));
	}
	
	private void failRemoveMember(
			final GroupID gid,
			final UserName member,
			final Exception expected) {
		try {
			manager.storage.removeMember(gid, member);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
	}
	
	@Test
	public void storeAndGetRequestMinimal() throws Exception {
		final UUID id = UUID.randomUUID();
		manager.storage.storeRequest(GroupRequest.getBuilder(
				new RequestID(id), new GroupID("foo"), new UserName("bar"),
					CreateModAndExpireTimes.getBuilder(
							Instant.ofEpochMilli(20000), Instant.ofEpochMilli(30000))
					.build())
				.build());
		
		assertThat("incorrect request", manager.storage.getRequest(new RequestID(id)),
				is(GroupRequest.getBuilder(
						new RequestID(id), new GroupID("foo"), new UserName("bar"),
						CreateModAndExpireTimes.getBuilder(
								Instant.ofEpochMilli(20000), Instant.ofEpochMilli(30000))
						.build())
						.build()));
	}
	
	@Test
	public void storeAndGetRequestMaximal() throws Exception {
		final UUID id = UUID.randomUUID();
		manager.storage.storeRequest(GroupRequest.getBuilder(
				new RequestID(id), new GroupID("foobar"), new UserName("barfoo"),
					CreateModAndExpireTimes.getBuilder(
							Instant.ofEpochMilli(40000), Instant.ofEpochMilli(60000))
					.withModificationTime(Instant.ofEpochMilli(50000))
					.build())
				.withInviteToGroup(new UserName("target"))
				.withStatus(GroupRequestStatus.from(
						GroupRequestStatusType.DENIED, new UserName("whee"), "jerkface"))
				.build());
		
		assertThat("incorrect request", manager.storage.getRequest(new RequestID(id)),
				is(GroupRequest.getBuilder(
						new RequestID(id), new GroupID("foobar"), new UserName("barfoo"),
						CreateModAndExpireTimes.getBuilder(
								Instant.ofEpochMilli(40000), Instant.ofEpochMilli(60000))
						.withModificationTime(Instant.ofEpochMilli(50000))
						.build())
					.withInviteToGroup(new UserName("target"))
					.withStatus(GroupRequestStatus.denied(new UserName("whee"), "jerkface"))
					.build()));
	}
	
	//TODO TEST that saving requests with similar but not identical characteristics works
	//TODO TEST that saving requests with identical characteristics but with open vs. closed states works
	
	@Test
	public void storeRequestFailDuplicateID() throws Exception {
		final UUID id = UUID.randomUUID();
		manager.storage.storeRequest(GroupRequest.getBuilder(
				new RequestID(id), new GroupID("foo"), new UserName("bar"),
					CreateModAndExpireTimes.getBuilder(
							Instant.ofEpochMilli(20000), Instant.ofEpochMilli(30000))
					.build())
				.build());
		
		final GroupRequest request = GroupRequest.getBuilder(
				new RequestID(id), new GroupID("foo1"), new UserName("bar1"),
				CreateModAndExpireTimes.getBuilder(
						Instant.ofEpochMilli(30000), Instant.ofEpochMilli(40000))
				.build())
			.build();
		
		failStoreRequest(request, new IllegalArgumentException(String.format(
				"ID %s already exists in the database. The programmer is responsible for " +
				"maintaining unique IDs.",
				id.toString())));
	}
	
	@Test
	public void storeRequestFailEquivalentRequestNoTarget() throws Exception {
		final UUID id = UUID.randomUUID();
		manager.storage.storeRequest(GroupRequest.getBuilder(
				new RequestID(id), new GroupID("foo"), new UserName("bar"),
					CreateModAndExpireTimes.getBuilder(
							Instant.ofEpochMilli(20000), Instant.ofEpochMilli(30000))
					.build())
				.build());
		
		final GroupRequest request = GroupRequest.getBuilder(
				new RequestID(UUID.randomUUID()), new GroupID("foo"), new UserName("bar"),
				CreateModAndExpireTimes.getBuilder(
						Instant.ofEpochMilli(30000), Instant.ofEpochMilli(40000))
				.build())
			.build();
		
		failStoreRequest(request, new RequestExistsException(String.format(
				"Request exists with ID: %s", id.toString())));
	}
	
	@Test
	public void storeRequestFailEquivalentRequestWithTarget() throws Exception {
		final UUID id = UUID.randomUUID();
		manager.storage.storeRequest(GroupRequest.getBuilder(
				new RequestID(id), new GroupID("foo1"), new UserName("bar1"),
					CreateModAndExpireTimes.getBuilder(
							Instant.ofEpochMilli(20000), Instant.ofEpochMilli(30000))
					.build())
				.withInviteToGroup(new UserName("baz1"))
				.build());
		
		final GroupRequest request = GroupRequest.getBuilder(
				new RequestID(UUID.randomUUID()), new GroupID("foo1"), new UserName("bar1"),
				CreateModAndExpireTimes.getBuilder(
						Instant.ofEpochMilli(30000), Instant.ofEpochMilli(40000))
				.build())
				.withInviteToGroup(new UserName("baz1"))
			.build();
		
		failStoreRequest(request, new RequestExistsException(String.format(
				"Request exists with ID: %s", id.toString())));
	}
	
	private void failStoreRequest(final GroupRequest request, final Exception expected) {
		
		try {
			manager.storage.storeRequest(request);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, expected);
		}
	}
	
}
