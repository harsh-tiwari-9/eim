package com.jio.eim.inventory.service;

import com.jio.eim.inventory.dto.InventoryRequest;
import com.jio.eim.inventory.dto.InventoryRequest.ProfileDto;
import com.jio.eim.inventory.dto.InventoryResponse;
import com.jio.eim.inventory.dto.InventoryResponse.CertSummary;
import com.jio.eim.inventory.dto.InventoryResponse.IpaCapabilitiesView;
import com.jio.eim.inventory.dto.InventoryResponse.ProfileView;
import com.jio.eim.inventory.dto.PagedResponse;
import com.jio.eim.inventory.entity.DeviceProfile;
import com.jio.eim.inventory.entity.EuiccCert;
import com.jio.eim.inventory.entity.InventoryDevice;
import com.jio.eim.inventory.entity.IpaCapabilities;
import com.jio.eim.inventory.repository.DeviceProfileRepository;
import com.jio.eim.inventory.repository.EuiccCertRepository;
import com.jio.eim.inventory.repository.InventoryDeviceRepository;
import com.jio.eim.inventory.ingest.RegisterResult;
import com.jio.eim.inventory.repository.IpaCapabilitiesRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InventoryService {

    private static final String REGISTERED = "REGISTERED";
    private static final String DELETED = "DELETED";

    private final InventoryDeviceRepository deviceRepository;
    private final DeviceProfileRepository profileRepository;
    private final IpaCapabilitiesRepository capabilitiesRepository;
    private final EuiccCertRepository certRepository;
    private final CertificateService certificateService;

    public InventoryService(
            InventoryDeviceRepository deviceRepository,
            DeviceProfileRepository profileRepository,
            IpaCapabilitiesRepository capabilitiesRepository,
            EuiccCertRepository certRepository,
            CertificateService certificateService) {
        this.deviceRepository = deviceRepository;
        this.profileRepository = profileRepository;
        this.capabilitiesRepository = capabilitiesRepository;
        this.certRepository = certRepository;
        this.certificateService = certificateService;
    }

    @Transactional
    public InventoryResponse register(InventoryRequest request) {
        RegisterResult result = registerInternal(request);
        InventoryDevice device = deviceRepository.findById(request.getEid())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Device not saved"));
        return buildResponse(device, result.certSummary(), result.remarks());
    }

    public boolean deviceExists(String eid) {
        return deviceRepository.existsById(eid);
    }

    @Transactional
    public RegisterResult registerInternal(InventoryRequest request) {
        Instant now = Instant.now();

        InventoryDevice device = deviceRepository.findById(request.getEid()).orElse(new InventoryDevice());
        if (device.getRegisteredAt() == null) {
            device.setRegisteredAt(now);
        }
        device.setEid(request.getEid());
        device.setOwnerId(request.getOwnerId());
        device.setAutoEnable(parseBoolean(request.getAutoEnable()));
        device.setAutoDelete(parseBoolean(request.getAutoDelete()));
        device.setStatus(REGISTERED);
        device.setUpdatedAt(now);
        deviceRepository.save(device);

        profileRepository.deleteByEid(request.getEid());
        profileRepository.flush();
        if (request.getProfiles() != null) {
            for (ProfileDto profileDto : request.getProfiles()) {
                DeviceProfile profile = new DeviceProfile();
                profile.setEid(request.getEid());
                profile.setIccid(profileDto.getIccid());
                profile.setState(profileDto.getState());
                profile.setProfileClass(profileDto.getProfileClass());
                profile.setFallback(false);
                profileRepository.save(profile);
            }
        }

        IpaCapabilities caps = capabilitiesRepository.findById(request.getEid()).orElse(new IpaCapabilities());
        caps.setEid(request.getEid());
        if (request.getIpaCapabilities() != null) {
            caps.setDirectRspServerCommunication(request.getIpaCapabilities().isDirectRspServerCommunication());
            caps.setIndirectRspServerCommunication(request.getIpaCapabilities().isIndirectRspServerCommunication());
        }
        capabilitiesRepository.save(caps);

        var certDto = request.getEuiccEumCerts().getFirst();
        CertSummary certSummary = certificateService.validateAndExtract(certDto);

        EuiccCert cert = certRepository.findById(request.getEid()).orElse(new EuiccCert());
        cert.setEid(request.getEid());
        cert.setEuiccCertBase64(certDto.getEuiccCertAsBase64());
        cert.setEumCertBase64(certDto.getEumCertAsBase64());
        cert.setEuiccPublicKeyHex(certSummary.getEuiccPublicKeyHex());
        cert.setEuiccSubject(certSummary.getEuiccSubject());
        cert.setEumSubject(certSummary.getEumSubject());
        cert.setCertValidFrom(certSummary.getCertValidFrom());
        cert.setCertValidTo(certSummary.getCertValidTo());
        cert.setChainValid(certSummary.isChainValid());
        certRepository.save(cert);

        String remarks = certSummary.isChainValid()
                ? "Device registered — certificate chain valid"
                : "Device registered — certificate chain invalid";

        return new RegisterResult(certSummary, remarks);
    }

    @Transactional(readOnly = true)
    public InventoryResponse get(String eid) {
        InventoryDevice device = deviceRepository.findById(eid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Device not found"));
        if (DELETED.equals(device.getStatus())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Device not found");
        }
        EuiccCert cert = certRepository.findById(eid).orElse(null);
        CertSummary certSummary = cert == null ? null : toCertSummary(cert);
        return buildResponse(device, certSummary, "Device retrieved");
    }

    @Transactional(readOnly = true)
    public PagedResponse<InventoryResponse> list(
            String ownerId, String status, String search, Pageable pageable) {
        Page<InventoryDevice> devicesPage = deviceRepository.search(
                blankToNull(ownerId),
                blankToNull(status),
                blankToNull(search),
                pageable);

        List<InventoryResponse> content = devicesPage.getContent().stream()
                .map(d -> buildResponse(
                        d,
                        certRepository.findById(d.getEid()).map(this::toCertSummary).orElse(null),
                        null))
                .toList();

        return PagedResponse.from(devicesPage, content);
    }

    @Transactional
    public void delete(String eid) {
        InventoryDevice device = deviceRepository.findById(eid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Device not found"));
        if (DELETED.equals(device.getStatus())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Device not found");
        }
        device.setStatus(DELETED);
        device.setUpdatedAt(Instant.now());
        deviceRepository.save(device);
    }

    private InventoryResponse buildResponse(InventoryDevice device, CertSummary certSummary, String ignoredMessage) {
        InventoryResponse response = new InventoryResponse();
        response.setEid(device.getEid());
        response.setOwnerId(device.getOwnerId());
        response.setStatus(device.getStatus());
        response.setAutoEnable(Boolean.toString(device.isAutoEnable()));
        response.setAutoDelete(Boolean.toString(device.isAutoDelete()));

        response.setProfiles(profileRepository.findByEid(device.getEid()).stream()
                .map(p -> {
                    ProfileView view = new ProfileView();
                    view.setIccid(p.getIccid());
                    view.setState(p.getState());
                    view.setProfileClass(p.getProfileClass());
                    return view;
                })
                .toList());

        capabilitiesRepository.findById(device.getEid()).ifPresent(caps -> {
            IpaCapabilitiesView view = new IpaCapabilitiesView();
            view.setDirectRspServerCommunication(caps.isDirectRspServerCommunication());
            view.setIndirectRspServerCommunication(caps.isIndirectRspServerCommunication());
            response.setIpaCapabilities(view);
        });

        response.setCertInfo(certSummary);
        return response;
    }

    private CertSummary toCertSummary(EuiccCert cert) {
        CertSummary summary = new CertSummary();
        summary.setChainValid(cert.isChainValid());
        summary.setEuiccSubject(cert.getEuiccSubject());
        summary.setEumSubject(cert.getEumSubject());
        summary.setEuiccPublicKeyHex(cert.getEuiccPublicKeyHex());
        summary.setCertValidFrom(cert.getCertValidFrom());
        summary.setCertValidTo(cert.getCertValidTo());
        return summary;
    }

    private boolean parseBoolean(String value) {
        if (value == null) {
            return false;
        }
        return Boolean.parseBoolean(value.trim());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
