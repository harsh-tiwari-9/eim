-- package_format was VARCHAR(10), which fit 'ASN1' and 'DL_TRIGGER' exactly but overflows on newer
-- unsigned formats such as 'EUICC_DATA_REQ' (IpaEuiccDataRequest). Widen it so the label is a plain
-- descriptor, not a length-constrained code.
ALTER TABLE psmo.signed_packages ALTER COLUMN package_format TYPE VARCHAR(32);