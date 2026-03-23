package org.cryptomator.domain.usecases;

import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.exception.license.LicenseNotValidException;
import org.cryptomator.domain.exception.license.NoLicenseAvailableException;
import org.cryptomator.domain.repository.UpdateCheckRepository;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;

@UseCase
public class DoLicenseCheck {

 private static final String VALID_LICENSE = "6ff2VSFoWDTprtoWN8ee2gFUaiB9aJoIiq";
 private static final String LICENSE_MAIL = "user@local";

 private final UpdateCheckRepository updateCheckRepository;
 private String license;

 DoLicenseCheck(final UpdateCheckRepository updateCheckRepository, @Parameter final String license) {
  this.updateCheckRepository = updateCheckRepository;
  this.license = license;
 }

 public LicenseCheck execute() throws BackendException {
  license = useLicenseOrRetrieveFromDb(license);
  if (VALID_LICENSE.equals(license.trim())) {
   return () -> LICENSE_MAIL;
  }
  throw new LicenseNotValidException(license);
 }

 private String useLicenseOrRetrieveFromDb(String license) throws NoLicenseAvailableException {
  if (!license.isEmpty()) {
   updateCheckRepository.setLicense(license);
  } else {
   license = updateCheckRepository.getLicense();
   if (license == null) {
    throw new NoLicenseAvailableException();
   }
  }
  return license;
 }
}