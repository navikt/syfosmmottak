package no.nav.syfo

enum class SamhandlerPraksisType(val string: String) {
    FASTLEGE("FALE"),
    FASTLONNET("FALO"),
    TURNUSLEGE_FASTLONNET("FATU"),
    SPESIALIST("SPES"),
    SPESIALIST_BARNESYKDOMMER("SPBA"),
    SPESIALIST_INDREMEDISIN("SPIN"),
    SPESIALIST_KIRURGI("SPKI"),
    SPESIALIST_OYELEGE("SPOL"),
    SPESIALIST_ORE_NESE_HALS("SPON"),
    SPESIALIST_GYNEKOLOGI("SPGY"),
    SPESIALIST_PSYKIATRI("SPPS"),
    SPESIALIST_HUDLEGE("SPHU"),
    SPESIALIST_FYSIKALSK_MEDISIN_OG_REHABILITERING("SPFY"),
    SPESIALIST_ANESTESIOLOGI("SPAN"),
    SPESIALIST_NEVROLOGI("SPNE"),
    SPESIALIST_RADIOLOGI("SPRA"),
    SPESIALIST_ONKOLOGI("SPOK"),
    SPESIALIST_REVMATOLOGI("SPRE"),
    HELSESTASJON("HELS"),
    LEGEVAKT("LEVA"),
    LEGEVAKT_KOMMUNAL("LEKO"),
    UTEN_REFUSJONSRETT("URRE"),
    UTDANNINGSKANDIDAT("UTKA"),
    SYKEPLEIER("SYPL"),
    FASTLONNSTILSKUDD_FYSIOTERAPI("FAFY"),
    FYSIOTERAPEUT_KOMMUNAL("FYKO"),
    FYSIOTERAPEUT("FYNO"),
    MANUELLTERAPEUT("FYMT"),
    PSYKOMOTORIKER("FYPM"),
    FYSIOTERAPEUT_RIDETERAPI("FYRT"),
    UTDANNINGSKANDIDAT_MANUELL_TERAPI("FYUM"),
    UTDANNINGSKANDIDAT_FYSIO_ANNET("FYUV"),
    UTDANNINGSKANDIDAT_PSYKOMOTORIKK("FYUP"),
    KIROPRAKTOR("KINO"),
    PSYKOLOG("PSNO"),
    NEVROPSYKOLOG("PSNE"),
    UTDANNINGSKANDIDAT_PSYKOLOG("PSUT"),
    TANNLEGE("TANN"),
    TANNPLEIER("TAPL"),
    TANNPLEIER_OFFENTLIG("TAPO"),
    TANNLEGE_KJEVEORTOPED("TAKJ"),
    FYLKESKOMMUNAL_TANNLEGE("TAFY"),
    FYLKESKOMMUNAL_KJEVEORTOPED("TAFK"),
    LOGOPED("LOGO"),
    JORDMOR("JORD"),
    ORTOPTIST("ORTO"),
    AUDIOPEDAGOG("AUDI")
}