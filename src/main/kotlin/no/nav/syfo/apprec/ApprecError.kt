package no.nav.syfo.apprec

enum class ApprecError(val v: String, val dn: String, val s: String) {
    GEN_DATE_ERROR("X99", "Meldingens dato datert(gendate) er nyere enn dagens dato", "2.16.578.1.12.4.1.1.8221"),
    BEHANDLER_PERSON_NUMBER_NOT_VALID("21", "Behandlers fødselsnummer er ikke et gyldig fødselsnummer.",
            "2.16.578.1.12.4.1.1.8222"),
    PATIENT_PERSON_NUMBER_NOT_FOUND_IN_SCHEMA("30", "Pasientens fødselsnummer finnes ikke i skjemaet.",
            "2.16.578.1.12.4.1.1.8222"),
    PATIENT_PERSON_NUMBER_IS_WRONG("31", "Pasientens fødselsnummer er feil.", "2.16.578.1.12.4.1.1.8222"),
    PATIENT_NAME_IS_NOT_IN_SCHEMA("32", "Pasientens navn finnes ikke på skjemaet.",
            "2.16.578.1.12.4.1.1.8222"),
    PATIENT_LASTNAME_IS_NOT_IN_SCHEMA("33", "Pasientens etternavn finnes ikke på skjemaet.",
            "2.16.578.1.12.4.1.1.8222"),
    PATIENT_PERSON_NUMBER_OR_DNUMBER_MISSING_IN_POPULATION_REGISTER("53",
            "Pasientens fødselsnummer eller D-nummer finnes ikke registrert i Folkeregisteret.",
            "2.16.578.1.12.4.1.1.8222"),
    DUPLICATE("54", "Duplikat! - Denne legeerklæringen meldingen er mottatt tidligere. Skal ikke sendes på nytt.",
            "2.16.578.1.12.4.1.1.8222"),
    MISSING_PATIENT_INFO("55", "Pasientopplysninger er utilstrekkelige", "2.16.578.1.12.4.1.1.8222"),
}
