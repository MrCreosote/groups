package us.kbase.test.groups.core;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.util.Optional;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import us.kbase.groups.core.FieldItem.StringField;
import us.kbase.groups.core.GroupID;
import us.kbase.groups.core.GroupName;
import us.kbase.groups.core.GroupType;
import us.kbase.groups.core.GroupUpdateParams;
import us.kbase.groups.core.OptionalGroupFields;
import us.kbase.test.groups.TestCommon;

public class GroupUpdateParamsTest {

	@Test
	public void equals() throws Exception {
		EqualsVerifier.forClass(GroupUpdateParams.class).usingGetClass().verify();
	}
	
	@Test
	public void buildMinimal() throws Exception {
		final GroupUpdateParams p = GroupUpdateParams.getBuilder(new GroupID("id")).build();
		
		assertThat("incorrect id", p.getGroupID(), is(new GroupID("id")));
		assertThat("incorrect name", p.getGroupName(), is(Optional.empty()));
		assertThat("incorrect fields", p.getOptionalFields(),
				is(OptionalGroupFields.getBuilder().build()));
		assertThat("incorrect type", p.getType(), is(Optional.empty()));
	}
	
	@Test
	public void buildMaximal() throws Exception {
		final GroupUpdateParams p = GroupUpdateParams.getBuilder(new GroupID("id"))
				.withName(new GroupName("n"))
				.withType(GroupType.TEAM)
				.withOptionalFields(OptionalGroupFields.getBuilder()
						.withDescription(StringField.remove()).build())
				.build();
		
		assertThat("incorrect id", p.getGroupID(), is(new GroupID("id")));
		assertThat("incorrect name", p.getGroupName(), is(Optional.of(new GroupName("n"))));
		assertThat("incorrect fields", p.getOptionalFields(), is(OptionalGroupFields.getBuilder()
						.withDescription(StringField.remove()).build()));
		assertThat("incorrect type", p.getType(), is(Optional.of(GroupType.TEAM)));
	}
	
	@Test
	public void getBuilderFail() throws Exception {
		try {
			GroupUpdateParams.getBuilder(null);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new NullPointerException("id"));
		}
	}
	
	@Test
	public void withNameFail() throws Exception {
		try {
			GroupUpdateParams.getBuilder(new GroupID("i")).withName(null);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new NullPointerException("name"));
		}
	}
	
	@Test
	public void withTypeFail() throws Exception {
		try {
			GroupUpdateParams.getBuilder(new GroupID("i")).withType(null);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new NullPointerException("type"));
		}
	}
	
	@Test
	public void withFieldsFail() throws Exception {
		try {
			GroupUpdateParams.getBuilder(new GroupID("i")).withOptionalFields(null);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new NullPointerException("fields"));
		}
	}
	
}
