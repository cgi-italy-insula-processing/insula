package com.cgi.eoss.platform.core.processing.server.persistence.service;

import static com.cgi.eoss.platform.core.processing.server.model.QPlatformService.platformService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.apache.commons.codec.binary.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.core.processing.server.model.PlatformServiceContextFile;
import com.cgi.eoss.platform.core.processing.server.model.User;
import com.cgi.eoss.platform.core.processing.server.persistence.dao.PlatformEntityDao;
import com.cgi.eoss.platform.core.processing.server.persistence.dao.PlatformServiceDao;
import com.querydsl.core.types.Predicate;

@Service
@Transactional(readOnly = true)
public class JpaServiceDataService extends AbstractJpaDataService<PlatformService> implements ServiceDataService {

    private final PlatformServiceDao platformServiceDao;

    private final ServiceFileDataService serviceFilesDataService;

    private final ProcessingCoreDataInitializationService processingCoreDataInitializationService;

    
    @Autowired
    public JpaServiceDataService(PlatformServiceDao platformServiceDao, ServiceFileDataService serviceFilesDataService,
                                 ProcessingCoreDataInitializationService processingCoreDataInitializationService) {
        this.platformServiceDao = platformServiceDao;
        this.serviceFilesDataService = serviceFilesDataService;
        this.processingCoreDataInitializationService = processingCoreDataInitializationService;
    }

    @Override
    protected PlatformEntityDao<PlatformService> getDao() {
        return platformServiceDao;
    }

    @Override
    protected Predicate getUniquePredicate(PlatformService entity) {
        return platformService.name.eq(entity.getName());
    }

    @Override
    public List<PlatformService> search(String term) {
        return platformServiceDao.findByNameContainingIgnoreCase(term);
    }

    @Override
    public List<PlatformService> findByOwner(User user) {
        return platformServiceDao.findByOwner(user);
    }

    @Override
    public Optional<PlatformService> getByName(String serviceName) {
        return platformServiceDao.findOne(platformService.name.eq(serviceName));
    }

    @Override
    public List<PlatformService> findAllAvailable() {
        return platformServiceDao.findByStatus(PlatformService.Status.AVAILABLE);
    }

    @Override
    @Transactional(readOnly = true)
    public String computeServiceFingerprint(PlatformService platformService) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            List<PlatformServiceContextFile> serviceFiles = serviceFilesDataService.findByService(platformService);
            serviceFiles.sort(Comparator.comparing(PlatformServiceContextFile::getFilename));
            for (PlatformServiceContextFile contextFile : serviceFiles) {
                bos.write(contextFile.getFilename().getBytes());
                bos.write(contextFile.getContent().getBytes());
            }
            MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
            byte[] serviceSerialized = bos.toByteArray();
            digest.update(serviceSerialized);
            String md5 = Hex.encodeHexString(digest.digest());
            return md5;

        } catch (IOException | NoSuchAlgorithmException e) {
            throw new IllegalStateException();
        }

    }

	@Override
	public String getPlatformDockerPrefix() {
		return processingCoreDataInitializationService.getPlatformDockerPrefix();
	}

}
