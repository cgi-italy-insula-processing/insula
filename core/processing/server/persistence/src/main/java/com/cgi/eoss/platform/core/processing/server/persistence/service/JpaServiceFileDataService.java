package com.cgi.eoss.platform.core.processing.server.persistence.service;

import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.core.processing.server.model.PlatformServiceContextFile;
import com.cgi.eoss.platform.core.processing.server.persistence.dao.PlatformEntityDao;
import com.cgi.eoss.platform.core.processing.server.persistence.dao.PlatformServiceContextFileDao;
import com.querydsl.core.types.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.cgi.eoss.platform.core.processing.server.model.QPlatformServiceContextFile.platformServiceContextFile;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class JpaServiceFileDataService extends AbstractJpaDataService<PlatformServiceContextFile> implements ServiceFileDataService {

    private final PlatformServiceContextFileDao platformServiceContextFileDao;

    @Autowired
    public JpaServiceFileDataService(PlatformServiceContextFileDao platformServiceContextFileDao) {
        this.platformServiceContextFileDao = platformServiceContextFileDao;
    }

    @Override
    protected PlatformEntityDao<PlatformServiceContextFile> getDao() {
        return platformServiceContextFileDao;
    }

    @Override
    protected Predicate getUniquePredicate(PlatformServiceContextFile entity) {
        return platformServiceContextFile.service.eq(entity.getService()).and(platformServiceContextFile.filename.eq(entity.getFilename()));
    }

    @Override
    public List<PlatformServiceContextFile> findByService(PlatformService service) {
        return platformServiceContextFileDao.findByService(service);
    }

}
