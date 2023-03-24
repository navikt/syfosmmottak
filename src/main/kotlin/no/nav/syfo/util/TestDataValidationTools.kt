package no.nav.syfo.util

fun erTestFnr(fnr: String): Boolean {
    val testFnr = listOf(
        "14077700162", "23077200290", "19095800273", "19079800468", "09090950972", "16126800464",
        "19128600143", "22047800106", "21016400952", "07070750710", "12119000465", "15040650560", "28027000608",
        "13031353453", "15045400112", "04129700489", "12050050295", "02117800213", "18118500284", "03117000205",
        "08077000292", "20086600138", "02039000183", "11028600374", "13046700125", "28096900254", "14076800236",
        "03117800252", "11079500412", "15076500565", "15051555535", "21030550231", "13116900216", "04056600324",
        "14019800513", "05073500186", "12057900499", "24048600332", "17108300566", "01017112364", "11064700342",
        "29019900248",
    )
    return testFnr.contains(fnr)
}
