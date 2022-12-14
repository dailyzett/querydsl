package study.querydsl;

import static com.querydsl.jpa.JPAExpressions.select;
import static org.assertj.core.api.Assertions.assertThat;
import static study.querydsl.entity.QMember.member;
import static study.querydsl.entity.QTeam.team;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import java.util.Objects;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceContext;
import javax.persistence.PersistenceUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import study.querydsl.dto.MemberDto;
import study.querydsl.dto.QMemberDto;
import study.querydsl.dto.UserDto;
import study.querydsl.entity.Member;
import study.querydsl.entity.QMember;
import study.querydsl.entity.Team;

@SpringBootTest
@Transactional
public class QueryDslBasicTest {

	@PersistenceContext
	EntityManager em;

	JPAQueryFactory queryFactory = null;

	@BeforeEach
	void before() {
		queryFactory = new JPAQueryFactory(em);

		Team teamA = new Team("teamA");
		Team teamB = new Team("teamB");
		em.persist(teamA);
		em.persist(teamB);

		Member member1 = new Member("member1", 10, teamA);
		Member member2 = new Member("member2", 20, teamA);
		Member member3 = new Member("member3", 30, teamB);
		Member member4 = new Member("member4", 40, teamB);

		em.persist(member1);
		em.persist(member2);
		em.persist(member3);
		em.persist(member4);
	}

	@Test
	void startJPQL() {
		//member1 JPQL
		Member findByJPQL = em.createQuery("select m from Member m where m.username = :username",
				Member.class)
			.setParameter("username", "member1")
			.getSingleResult();

		assertThat(findByJPQL.getUsername()).isEqualTo("member1");
	}

	@Test
	void startDSL() {
		Member findMember = queryFactory
			.select(member)
			.from(member)
			.where(member.username.eq("member1"))
			.fetchOne();

		assertThat(Objects.requireNonNull(findMember).getUsername()).isEqualTo("member1");
	}

	@Test
	void search() {
		Member member1 = queryFactory
			.selectFrom(member)
			.where(member.username.eq("member1").and(member.age.eq(10)))
			.fetchOne();

		assertThat(Objects.requireNonNull(member1).getUsername()).isEqualTo("member1");
	}

	@Test
	void searchAndParam() {
		Member member1 = queryFactory
			.selectFrom(member)
			.where(
				member.username.eq("member1"),
				member.age.eq(10)
			)
			.fetchOne();

		assertThat(Objects.requireNonNull(member1).getUsername()).isEqualTo("member1");
	}

	@Test
	void resultFetchTest() {
		List<Member> result = queryFactory
			.selectFrom(member)
			.where(
				member.username.eq("member1"),
				member.age.eq(10)
			).fetch();

		assertThat(result.size()).isEqualTo(1);

		Long totalCount = queryFactory
			.select(member.count())
			.from(member)
			.fetchOne();

		System.out.println("totalCount = " + totalCount);
	}

	/**
	 * ?????? ????????? ??? NULL ??? ?????? ?????? ???????????? ??????
	 * ?????? ????????? ???
	 */

	@Test
	void sort() {

		em.persist(new Member(null, 100));
		em.persist(new Member("member5", 100));
		em.persist(new Member("member6", 100));

		List<Member> result = queryFactory
			.selectFrom(member)
			.where(member.age.eq(100))
			.orderBy(member.age.desc(), member.username.asc().nullsLast())
			.fetch();

		assertThat(result.get(0).getUsername()).isEqualTo("member5");
		assertThat(result.get(1).getUsername()).isEqualTo("member6");
		assertThat(result.get(2).getUsername()).isNull();
	}

	@Test
	void paging1() {
		List<Member> result = queryFactory
			.selectFrom(member)
			.orderBy(member.username.desc())
			.offset(1)
			.limit(2)
			.fetch();

		assertThat(result.size()).isEqualTo(2);
		for (Member member1 : result) {
			System.out.println("member1 = " + member1);
		}
	}

	@Test
	void aggregation() {

		//?????? DSL ??? ???????????? ?????? ??????
		//???????????? DTO ??? ???????????? ???????????? ?????? ????????????.
		List<Tuple> fetch = queryFactory
			.select(
				member.count(),
				member.age.sum(),
				member.age.avg(),
				member.age.min()
			)
			.from(member)
			.fetch();

		Tuple tuple = fetch.get(0);
		assertThat(tuple.get(member.count())).isEqualTo(4);
		assertThat(tuple.get(member.age.sum())).isEqualTo(100);
	}

	/**
	 * ?????? ????????? ??? ?????? ?????? ????????? ?????????
	 */
	@Test
	void group() {
		List<Tuple> result = queryFactory
			.select(team.name, member.age.avg())
			.from(member)
			.join(member.team, team)
			.groupBy(team.name)
			.fetch();

		Tuple teamA = result.get(0);
		Tuple teamB = result.get(1);

		assertThat(teamA.get(team.name)).isEqualTo("teamA");
		assertThat(teamA.get(member.age.avg())).isEqualTo(15);

		assertThat(teamB.get(team.name)).isEqualTo("teamB");
		assertThat(teamB.get(member.age.avg())).isEqualTo(35);
	}

	@Test
	void join() {
		List<Member> result = queryFactory
			.selectFrom(member)
			.join(member.team, team)
			.where(team.name.eq("teamA"))
			.fetch();

		assertThat(result)
			.extracting("username")
			.containsExactly("member1", "member2");
	}

	/**
	 * ????????? ????????? ??? ????????? ?????? ?????? ??????
	 */
	@Test
	void thetaJoin() {
		em.persist(new Member("teamA"));
		em.persist(new Member("teamB"));
		em.persist(new Member("teamC"));

		List<Member> result = queryFactory
			.select(member)
			.from(member, team)
			.where(member.username.eq(team.name))
			.fetch();

		assertThat(result)
			.extracting("username")
			.containsExactly("teamA", "teamB");
	}

	/**
	 * ????????? ?????? ???????????????, ??? ????????? teamA??? ?????? ??????, ????????? ?????? ??????
	 */
	@Test
	void join_on_filtering() {
		List<Tuple> result = queryFactory
			.select(member, team)
			.from(member)
			.leftJoin(member.team, team)
			.on(team.name.eq("teamA"))
			.fetch();

		for (Tuple tuple : result) {
			System.out.println("tuple = " + tuple);
		}
	}

	/**
	 * ??????????????? ?????? ????????? ?????? ??????
	 */
	@Test
	void join_on_no_relation() {
		em.persist(new Member("teamA"));
		em.persist(new Member("teamB"));
		em.persist(new Member("teamC"));

		List<Tuple> result = queryFactory
			.select(member, team)
			.from(member)
			.leftJoin(team).on(member.username.eq(team.name))
			.fetch();

		for (Tuple tuple : result) {
			System.out.println("tuple = " + tuple);
		}
	}

	@PersistenceUnit
	EntityManagerFactory emf;

	@Test
	void fetchJoinNo() {
		//given
		em.flush();
		em.clear();

		Member findMember = queryFactory
			.selectFrom(member)
			.where(member.username.eq("member1"))
			.fetchOne();

		//when

		//then
		boolean loaded = emf.getPersistenceUnitUtil().isLoaded(Objects.requireNonNull(findMember).getTeam());
		assertThat(loaded).isFalse();
	}

	@Test
	void fetchJoinYes() {
		//given
		em.flush();
		em.clear();

		//when
		Member findMember = queryFactory
			.selectFrom(member)
			.join(member.team, team).fetchJoin()
			.where(member.username.eq("member1"))
			.fetchOne();

		//then
		boolean loaded = emf.getPersistenceUnitUtil().isLoaded(Objects.requireNonNull(findMember).getTeam());
		assertThat(loaded).isTrue();
	}

	/**
	 * ????????? ?????? ?????? ????????? ??????
	 */
	@Test
	void subQuery() {
		//given
		QMember memberSub = new QMember("memberSub");

		//when
		List<Member> result = queryFactory
			.selectFrom(member)
			.where(member.age.eq(
				select(memberSub.age.max())
					.from(memberSub)
			))
			.fetch();

		//then
		assertThat(result).extracting("age")
			.containsExactly(40);
	}

	/**
	 * ????????? ?????? ????????? ?????? ??????
	 */
	@Test
	void subQueryGoe() {
		//given
		QMember memberSub = new QMember("memberSub");

		//when
		List<Member> result = queryFactory
			.selectFrom(member)
			.where(member.age.goe(
				select(memberSub.age.avg())
					.from(memberSub)
			))
			.fetch();

		//then
		assertThat(result).extracting("age")
			.containsExactly(30, 40);
	}

	@Test
	void subQueryIn() {
		//given
		QMember memberSub = new QMember("memberSub");

		//when
		List<Member> result = queryFactory
			.selectFrom(member)
			.where(member.age.in(
				select(memberSub.age)
					.from(memberSub)
					.where(memberSub.age.gt(10))
			))
			.fetch();

		//then
		assertThat(result).extracting("age")
			.containsExactly(20, 30, 40);
	}

	@Test
	void selectSubQuery() {
		//given
		QMember memberSub = new QMember("memberSub");

		//when
		List<Tuple> fetch = queryFactory
			.select(member.username,
				select(memberSub.age.avg())
					.from(memberSub))
			.from(member)
			.fetch();

		//then
		for (Tuple tuple : fetch) {
			System.out.println("tuple = " + tuple);
		}
	}

	@Test
	void basicCase() {
		//given
		List<String> fetch = queryFactory
			.select(
				member.age
					.when(10).then("??????")
					.when(20).then("?????????")
					.otherwise("??????")
			)
			.from(member)
			.fetch();

		//when

		//then
		for (String s : fetch) {
			System.out.println("s = " + s);
		}
	}

	@Test
	void complexCase() {
		//given
		List<String> result = queryFactory
			.select(new CaseBuilder()
				.when(member.age.between(0, 20)).then("0~20")
				.when(member.age.between(21, 30)).then("21-30")
				.otherwise("??????"))
			.from(member)
			.fetch();
		//when

		//then
		for (String s : result) {
			System.out.println("s = " + s);
		}
	}

	@Test
	void constant() {
		//given
		List<Tuple> result = queryFactory
			.select(member.username, Expressions.constant("A"))
			.from(member)
			.fetch();

		for (Tuple tuple : result) {
			System.out.println("tuple = " + tuple);
		}
	}

	@Test
	void concat() {
		//given

		//{username}_{age}
		List<String> fetch = queryFactory
			.select(member.username.concat("_").concat(member.age.stringValue()))
			.from(member)
			.where(member.username.eq("member1"))
			.fetch();

		for (String s : fetch) {
			System.out.println("s = " + s);
		}
	}

	@Test
	void simpleProjection() {
		//given
		List<String> fetch = queryFactory
			.select(member.username)
			.from(member)
			.fetch();

		List<Tuple> tuples = queryFactory
			.select(member.username, member.age)
			.from(member)
			.fetch();

		for (Tuple tuple : tuples) {
			String username = tuple.get(member.username);
			Integer age = tuple.get(member.age);
			System.out.println("username = " + username);
			System.out.println("age = " + age);
		}

		/* ?????????????????? ??????????????? ?????? ????????? ??????.
		 * ?????????????????? DAO ??????????????? ????????? ??? ????????? ?????????.
		 * ?????? ???????????? ????????? ?????? DTO ??? ???????????? ?????? ??????.*/
	}

	/**
	 * New Operation ??? ?????????????????? ????????? ?????????. ??????????????? ?????? ??? ?????? ??????? ????????? ????????? ????????????.
	 */
	@Test
	void findDtoByJPQL() {
		List<MemberDto> resultList = em.createQuery(
				"select new study.querydsl.dto.MemberDto(m.username, m.age) "
					+ "from Member m", MemberDto.class)
			.getResultList();

		for (MemberDto memberDto : resultList) {
			System.out.println("memberDto = " + memberDto);
		}
	}

	@Test
	void findDtoBySetter() {
		//given
		List<MemberDto> fetch = queryFactory
			.select(Projections.bean(MemberDto.class, member.username, member.age))
			.from(member)
			.fetch();

		for (MemberDto memberDto : fetch) {
			System.out.println("memberDto = " + memberDto);
		}
	}

	@Test
	void findDtoByField() {
		//given
		List<MemberDto> fetch = queryFactory
			.select(Projections.fields(MemberDto.class, member.username, member.age))
			.from(member)
			.fetch();

		for (MemberDto memberDto : fetch) {
			System.out.println("memberDto = " + memberDto);
		}
	}

	@Test
	void findDtoByConstructor() {
		//given
		List<MemberDto> fetch = queryFactory
			.select(Projections.constructor(MemberDto.class, member.username, member.age))
			.from(member)
			.fetch();

		for (MemberDto memberDto : fetch) {
			System.out.println("memberDto = " + memberDto);
		}
	}

	/**
	 * ????????? ?????? ???
	 */
	@Test
	void findUserDtoByField() {
		//given
		List<UserDto> fetch = queryFactory
			.select(Projections.fields(UserDto.class, member.username.as("name"), member.age))
			.from(member)
			.fetch();

		for (UserDto memberDto : fetch) {
			System.out.println("memberDto = " + memberDto);
		}
	}

	/*
	 * ??????:
	 * 1. ?????? DTO ??????????????? ???????????? ????????? ????????? ??????
	 * 2. ????????? @QueryProjection ?????????????????? ???????????? ????????? ????????? ????????????
	 *
	 * ??????:
	 * 1. Q ????????? ????????????????????? ???
	 * 2. DTO ????????? QueryDSL ??? ?????? ??????????????? ???????????? ?????????.
	 * */
	@Test
	void findDtoByQueryProjection() {
		//given
		List<MemberDto> fetch = queryFactory
			.select(new QMemberDto(member.username, member.age))
			.from(member)
			.fetch();

		for (MemberDto memberDto : fetch) {
			System.out.println("memberDto = " + memberDto);
		}
	}

	/**
	 * 1. ?????? ?????? - BooleanBuilder ??????
	 */

	@Test
	void dynamicQuery_BooleanBuilder() {
		String usernameParam = "member1";
		Integer ageParam = 10;

		List<Member> result = searchMember1(usernameParam, ageParam);
		assertThat(result.size()).isEqualTo(1);
	}

	private List<Member> searchMember1(String usernameCond, Integer ageCond) {

		BooleanBuilder builder = new BooleanBuilder();

		if (usernameCond != null) {
			builder.and(member.username.eq(usernameCond));
		}

		if (ageCond != null) {
			builder.and(member.age.eq(ageCond));
		}

		return queryFactory
			.selectFrom(member)
			.where(builder)
			.fetch();
	}

	/**
	 * 2. Where ??? ?????? ?????? ???????????? ?????? where ????????? null ??? ???????????? ????????????.
	 */
	@Test
	void dynamicQueryWhereParameter() {
		String usernameParam = "member1";
		Integer ageParam = 10;

		List<Member> result = searchMember2(usernameParam, ageParam);
		assertThat(result.size()).isEqualTo(1);
	}

	private List<Member> searchMember2(String usernameCond, Integer ageCond) {
		return queryFactory
			.selectFrom(member)
//			.where(usernameEq(usernameCond), ageEq(ageCond))
			.where(allEq(usernameCond, ageCond))
			.fetch();
	}

	private BooleanExpression ageEq(Integer ageCond) {
		if (ageCond == null) {
			return null;
		}
		return member.age.eq(ageCond);
	}

	private BooleanExpression usernameEq(String usernameCond) {
		if (usernameCond == null) {
			return null;
		}
		return member.username.eq(usernameCond);
	}

	// ?????? ?????? isValid, ????????? IN : ??? ?????? ???????????? isServiceable
	// ?????? ????????? ????????? ????????? ??????????????? ????????? ??????.

	private BooleanExpression allEq(String usernameCond, Integer ageCond) {
		return usernameEq(usernameCond).and(ageEq(ageCond));
	}

	/**
	 * ?????? ????????? ????????? ????????????. ??? ?????? update ??? ?????? ?????? ????????? ?????? ?????? ????????? ???????????? ?????? ?????????. ??? ??? ?????? ????????? ??????????????????.
	 */

	@Test
	void bulkUpdate() {

		//member1 = 10 -> ?????????
		//member2 = 20 -> ?????????
		//????????? ??????

		//?????? ?????? ?????? ??? DB??? ????????? ????????? ???????????? ????????? ????????? ?????? ??????????????????.

		long count = queryFactory
			.update(member)
			.set(member.username, "?????????")
			.where(member.age.lt(28))
			.execute();

		em.flush();
		em.clear();

		List<Member> result = queryFactory
			.selectFrom(member)
			.fetch();

		for (Member member1 : result) {
			System.out.println("member1 = " + member1);
		}

		assertThat(count).isEqualTo(2);
	}

	@Test
	void bulkAdd() {
		//given
		long count = queryFactory
			.update(member)
			.set(member.age, member.age.add(1))
			.execute();
	}

	@Test
	void bulkDelete() {
		long count = queryFactory
			.delete(member)
			.where(member.age.gt(18))
			.execute();
	}

	/**
	 * SQL Function ????????????
	 */
	@Test
	void sqlFunction() {
		List<String> result = queryFactory
			.select(
				Expressions.stringTemplate("function('replace', {0}, {1}, {2})",
					member.username, "member", "M"))
			.from(member)
			.fetch();

		for (String s : result) {
			System.out.println("s = " + s);
		}
	}

	@Test
	void sqlFunction2() {
		List<String> fetch = queryFactory
			.select(member.username)
			.from(member)
//			.where(member.username.eq(Expressions.stringTemplate(
//				"function('lower', {0})", member.username
			.where(member.username.eq(member.username.lower()))
			.fetch();

		for (String s : fetch) {
			System.out.println("s = " + s);
		}
	}
}
