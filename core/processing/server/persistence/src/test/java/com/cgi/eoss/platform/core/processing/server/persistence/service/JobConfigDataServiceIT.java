package com.cgi.eoss.platform.core.processing.server.persistence.service;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import com.cgi.eoss.platform.core.processing.server.persistence.PersistenceCoreConfig;
import com.cgi.eoss.platform.core.processing.server.persistence.testutils.service.ProcessingCoreTestDataService;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.cgi.eoss.platform.core.processing.server.model.JobConfig;
import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.core.processing.server.model.User;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;

import java.util.List;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = { PersistenceCoreConfig.class })
@TestPropertySource("classpath:test-persistence-core.properties")
public class JobConfigDataServiceIT {

    @Rule
    public ExpectedException ex = ExpectedException.none();

    @Autowired
    private JobConfigDataService dataService;
    @Autowired
    private UserDataService userService;
    @Autowired
    private ServiceDataService svcService;

    @Autowired
    private ProcessingCoreTestDataService processingCoreTestDataService;

    @Before
    public void init() {
        processingCoreTestDataService.resourcesCleanup();
        processingCoreTestDataService.assertDbIsEmpty();
    }

    @After
    public void shutdown() {
        processingCoreTestDataService.resourcesCleanup();
        processingCoreTestDataService.assertDbIsEmpty();
    }

    @Test
    public void testSave_UpdatesJobConfigOnTheDb_WhenUniqueKeyOfNewJobConfigAndJobConfigOnTheDbMatches() {

        User owner = userService.save(new User("owner-uid"));

        PlatformService svc = new PlatformService("Test Service",owner,"dockerTag");
        svc = svcService.save(svc);

        JobConfig jobConfig = new JobConfig(owner, svc);
        ImmutableMultimap<String, String> stringStringImmutableMultimap
                = ImmutableMultimap.of("input1", "input1_Value1");
        jobConfig.setInputs(stringStringImmutableMultimap);
        jobConfig.setLabel("theJobConfigLabel");
        jobConfig = dataService.save(jobConfig);
        List<JobConfig> jobConfigs = dataService.getAll();
        assertThat(jobConfigs.size(), is(1));
        assertThat(jobConfigs.get(0).getLabel(), is("theJobConfigLabel"));

        JobConfig newJobConfig = new JobConfig(owner, svc);
        newJobConfig.setInputs(stringStringImmutableMultimap);
        newJobConfig.setLabel("theNeJobConfigLabel");
        newJobConfig = dataService.save(newJobConfig);

        jobConfigs = dataService.getAll();
        assertThat(jobConfigs.size(), is(1));
        assertThat(jobConfigs.get(0), is(newJobConfig));
        assertThat(jobConfigs.get(0).getId(), is(jobConfig.getId()));

        assertThat(jobConfigs.get(0).getLabel(), is("theNeJobConfigLabel"));
        assertThat(jobConfigs.get(0).getLabel(), is(newJobConfig.getLabel()));
        assertThat(jobConfigs.get(0).getParent(), is(jobConfig.getParent()));
    }


    @Test
    public void test() throws Exception {
        User owner = new User("owner-uid");
        User owner2 = new User("owner-uid2");
        userService.save(ImmutableSet.of(owner, owner2));

        PlatformService svc = new PlatformService("Test Service",owner,"dockerTag");;
        PlatformService svc2 = new PlatformService("Test Service2",owner,"dockerTag");;
        svc = svcService.save(svc);
        svc2 = svcService.save(svc2);

        Multimap<String, String> job1Inputs = ImmutableMultimap.of(
                "input1", "foo",
                "input2", "bar1",
                "input2", "bar2",
                "input3", "http://baz/?q=x,y&z={}");
        JobConfig jobConfig = new JobConfig(owner, svc);
        jobConfig.setInputs(job1Inputs);
        JobConfig jobConfig2 = new JobConfig(owner, svc2);
        dataService.save(ImmutableList.of(jobConfig, jobConfig2));

        assertThat(dataService.getAll(), is(ImmutableList.of(jobConfig, jobConfig2)));
        assertThat(dataService.getById(jobConfig.getId()).get(), is(jobConfig));
        assertThat(dataService.getByIds(ImmutableSet.of(jobConfig.getId())), is(ImmutableList.of(jobConfig)));
        assertThat(dataService.isUniqueAndValid(new JobConfig(owner, svc)), is(true));

        assertThat(dataService.findByOwner(owner), is(ImmutableList.of(jobConfig, jobConfig2)));
        assertThat(dataService.findByOwner(owner2), is(ImmutableList.of()));
        assertThat(dataService.findByService(svc), is(ImmutableList.of(jobConfig)));
        assertThat(dataService.findByService(svc2), is(ImmutableList.of(jobConfig2)));
        assertThat(dataService.findByOwnerAndService(owner, svc), is(ImmutableList.of(jobConfig)));
        assertThat(dataService.findByOwnerAndService(owner, svc2), is(ImmutableList.of(jobConfig2)));
        assertThat(dataService.findByOwnerAndService(owner2, svc), is(ImmutableList.of()));

        assertThat(dataService.getById(jobConfig.getId()).get().getInputs(), is(job1Inputs));
    }

    @Test
    public void testUniqueness() {
        User owner = new User("owner-uid");
        userService.save(ImmutableSet.of(owner));

        PlatformService svc =  new PlatformService("Test Service",owner,"dockerTag");
        svc = svcService.save(svc);

        JobConfig jobConfig = new JobConfig(owner, svc);

        JobConfig result = dataService.save(new JobConfig(owner, svc));

        // Verifies that a new JobConfig was not actually saved
        assertThat(result, is(jobConfig));
        assertThat(dataService.getAll(), is(ImmutableList.of(jobConfig)));
    }

}