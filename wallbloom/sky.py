"""The real sky behind the leaves.

Stars come from a small catalogue of real positions, and the moon from a standard
low-precision lunar theory. The view is centred on the painted moon with your zenith
straight up, so every star sits where it truly is relative to the moon, for your
place, date and time of night. The sky turns through the night as it really does.

Your place is worked out from your timezone setting, or set `location` in the config.
Nothing here uses the network or any location service.

The painted moon is much larger than life, and shows at night whether or not the real
moon has risen. When it hasn't, you are seeing the stars around where it really is.
"""

import datetime
import math
import os
import re

from .palette import SKY, STAR

# Right ascension in hours and declination in degrees, J2000. A star listed with
# capital letters in its key is first magnitude, or near enough, and shows brighter.
STARS = {
    "BETELGEUSE": (5.919, 7.41), "RIGEL": (5.242, -8.20), "BELLATRIX": (5.419, 6.35), "mintaka": (5.533, -0.30), "alnilam": (5.604, -1.20),
    "alnitak": (5.679, -1.94), "saiph": (5.796, -9.67),
    "ALDEBARAN": (4.599, 16.51), "elnath": (5.438, 28.61), "zeta_tau": (5.627, 21.14), "theta_tau": (4.478, 15.87), "gamma_tau": (4.330, 15.63),
    "delta_tau": (4.382, 17.54), "epsilon_tau": (4.477, 19.18), "alcyone": (3.791, 24.11),
    "CASTOR": (7.577, 31.89), "POLLUX": (7.755, 28.03), "alhena": (6.629, 16.40), "mebsuta": (6.732, 25.13), "wasat": (7.335, 21.98),
    "tejat": (6.383, 22.51), "mekbuda": (7.068, 20.57),
    "al_tarf": (8.275, 9.19), "asellus_a": (8.745, 18.15), "asellus_b": (8.721, 21.47), "acubens": (8.975, 11.86), "iota_cnc": (8.778, 28.76),
    "REGULUS": (10.139, 11.97), "denebola": (11.818, 14.57), "algieba": (10.333, 19.84), "zosma": (11.235, 20.52), "chertan": (11.237, 15.43),
    "eta_leo": (10.122, 16.76), "adhafera": (10.278, 23.42), "rasalas": (9.879, 26.01), "epsilon_leo": (9.764, 23.77),
    "SPICA": (13.420, -11.16), "porrima": (12.694, -1.45), "vindemiatrix": (13.036, 10.96), "auva": (12.927, 3.40), "zavijava": (11.845, 1.76),
    "heze": (13.578, -0.60),
    "zubeneschamali": (15.283, -9.38), "zubenelgenubi": (14.848, -16.04), "sigma_lib": (15.068, -25.28), "gamma_lib": (15.592, -14.79),
    "ANTARES": (16.490, -26.43), "dschubba": (16.006, -22.62), "acrab": (16.091, -19.81), "pi_sco": (15.981, -26.11), "sigma_sco": (16.353, -25.59),
    "tau_sco": (16.598, -28.22), "epsilon_sco": (16.836, -34.29), "mu_sco": (16.864, -38.05), "zeta_sco": (16.896, -42.36), "eta_sco": (17.203, -43.24),
    "theta_sco": (17.622, -43.00), "iota_sco": (17.793, -40.13), "kappa_sco": (17.708, -39.03), "shaula": (17.560, -37.10),
    "kaus_australis": (18.403, -34.38), "kaus_media": (18.350, -29.83), "kaus_borealis": (18.466, -25.42), "nunki": (18.921, -26.30), "ascella": (19.043, -29.88),
    "phi_sgr": (18.761, -26.99), "alnasl": (18.097, -30.42), "tau_sgr": (19.116, -27.67),
    "algedi": (20.300, -12.54), "dabih": (20.350, -14.78), "deneb_algedi": (21.784, -16.13), "nashira": (21.668, -16.66), "theta_cap": (21.099, -17.23),
    "omega_cap": (20.864, -26.92), "zeta_cap": (21.444, -22.41),
    "sadalsuud": (21.526, -5.57), "sadalmelik": (22.096, -0.32), "skat": (22.911, -15.82), "sadachbia": (22.361, -1.39), "zeta_aqr": (22.480, -0.02),
    "lambda_aqr": (22.877, -7.58), "eta_aqr": (22.589, -0.12), "theta_aqr": (22.281, -7.78),
    "alrescha": (2.034, 2.76), "eta_psc": (1.525, 15.35), "omicron_psc": (1.757, 9.16), "omega_psc": (23.988, 6.86), "iota_psc": (23.666, 5.63),
    "gamma_psc": (23.286, 3.28), "theta_psc": (23.466, 6.38), "epsilon_psc": (1.049, 7.89), "delta_psc": (0.811, 7.59),
    "hamal": (2.120, 23.46), "sheratan": (1.911, 20.81), "mesarthim": (1.892, 19.29), "ari_41": (2.833, 27.26),
    "menkar": (3.038, 4.09), "diphda": (0.727, -17.99),
    "markab": (23.079, 15.21), "scheat": (23.063, 28.08), "algenib": (0.221, 15.18), "alpheratz": (0.140, 29.09), "enif": (21.736, 9.88), "homam": (22.691, 10.83),
    "mirach": (1.162, 35.62), "almach": (2.065, 42.33), "delta_and": (0.655, 30.86),
    "mirfak": (3.405, 49.86), "algol": (3.136, 40.96), "zeta_per": (3.902, 31.88), "epsilon_per": (3.964, 40.01), "gamma_per": (3.080, 53.51),
    "CAPELLA": (5.278, 46.00), "menkalinan": (5.992, 44.95), "theta_aur": (5.995, 37.21), "iota_aur": (4.950, 33.17),
    "SIRIUS": (6.752, -16.72), "mirzam": (6.378, -17.96), "wezen": (7.140, -26.39), "adhara": (6.977, -28.97), "aludra": (7.402, -29.30),
    "PROCYON": (7.655, 5.22), "gomeisa": (7.453, 8.29),
    "dubhe": (11.062, 61.75), "merak": (11.031, 56.38), "phecda": (11.897, 53.69), "megrez": (12.257, 57.03), "alioth": (12.900, 55.96), "mizar": (13.399, 54.93),
    "alkaid": (13.792, 49.31),
    "schedar": (0.675, 56.54), "caph": (0.153, 59.15), "gamma_cas": (0.945, 60.72), "ruchbah": (1.430, 60.24), "segin": (1.907, 63.67),
    "DENEB": (20.690, 45.28), "sadr": (20.371, 40.26), "gienah_cyg": (20.770, 33.97), "delta_cyg": (19.750, 45.13), "albireo": (19.512, 27.96),
    "VEGA": (18.616, 38.78), "sheliak": (18.835, 33.36), "sulafat": (18.982, 32.69), "zeta_lyr": (18.746, 37.60), "delta_lyr": (18.908, 36.90),
    "ALTAIR": (19.846, 8.87), "tarazed": (19.771, 10.61), "alshain": (19.922, 6.41), "delta_aql": (19.425, 3.11), "zeta_aql": (19.090, 13.86), "lambda_aql": (19.104, -4.88),
    "ARCTURUS": (14.261, 19.18), "izar": (14.750, 27.07), "muphrid": (13.911, 18.40), "nekkar": (15.032, 40.39), "seginus": (14.535, 38.31), "rho_boo": (14.531, 30.37),
    "gienah_crv": (12.263, -17.54), "kraz": (12.573, -23.40), "algorab": (12.498, -16.52), "alchiba": (12.140, -24.73), "minkar": (12.169, -22.62),
    "rasalhague": (17.582, 12.56), "sabik": (17.173, -15.72), "cebalrai": (17.724, 4.57), "yed_prior": (16.239, -3.69), "zeta_oph": (16.619, -10.57), "kappa_oph": (16.961, 9.38),
    "acrux": (12.443, -63.10), "mimosa": (12.795, -59.69), "gacrux": (12.519, -57.11), "imai": (12.252, -58.75),
}
# Star patterns, used only to name what's showing. No lines are drawn on the sky.
CONSTELLATIONS = {
    "Orion": ["BETELGEUSE BELLATRIX", "BETELGEUSE alnitak", "BELLATRIX mintaka", "mintaka alnilam alnitak", "alnitak saiph", "mintaka RIGEL", "saiph RIGEL"],
    "Taurus": ["elnath epsilon_tau delta_tau gamma_tau", "zeta_tau ALDEBARAN theta_tau gamma_tau", "gamma_tau alcyone"],
    "Gemini": ["CASTOR mebsuta tejat", "POLLUX wasat mekbuda alhena", "CASTOR POLLUX"],
    "Cancer": ["al_tarf asellus_a asellus_b iota_cnc", "asellus_a acubens"],
    "Leo": ["REGULUS eta_leo algieba adhafera rasalas epsilon_leo", "algieba zosma denebola chertan REGULUS", "zosma chertan"],
    "Virgo": ["SPICA porrima zavijava", "porrima auva vindemiatrix", "auva heze SPICA"],
    "Libra": ["zubeneschamali zubenelgenubi sigma_lib", "zubeneschamali gamma_lib"],
    "Scorpius": ["acrab dschubba pi_sco", "dschubba sigma_sco ANTARES tau_sco epsilon_sco mu_sco zeta_sco eta_sco theta_sco iota_sco kappa_sco shaula"],
    "Sagittarius": ["alnasl kaus_media kaus_borealis phi_sgr nunki tau_sgr ascella phi_sgr", "kaus_media kaus_australis ascella", "alnasl kaus_australis"],
    "Capricornus": ["algedi dabih omega_cap zeta_cap deneb_algedi", "algedi theta_cap nashira deneb_algedi"],
    "Aquarius": ["sadalsuud sadalmelik sadachbia zeta_aqr eta_aqr", "sadalsuud theta_aqr lambda_aqr skat"],
    "Pisces": ["alrescha omicron_psc eta_psc", "alrescha epsilon_psc delta_psc omega_psc iota_psc gamma_psc theta_psc iota_psc"],
    "Aries": ["ari_41 hamal sheratan mesarthim"],
    "Cetus": ["menkar diphda"],
    "Pegasus": ["markab scheat alpheratz algenib markab", "markab homam enif"],
    "Andromeda": ["alpheratz delta_and mirach almach"],
    "Perseus": ["gamma_per mirfak algol zeta_per", "mirfak epsilon_per zeta_per"],
    "Auriga": ["CAPELLA menkalinan theta_aur elnath iota_aur CAPELLA"],
    "Canis Major": ["mirzam SIRIUS wezen adhara", "wezen aludra"],
    "Canis Minor": ["PROCYON gomeisa"],
    "Ursa Major": ["alkaid mizar alioth megrez dubhe merak phecda megrez"],
    "Cassiopeia": ["caph schedar gamma_cas ruchbah segin"],
    "Cygnus": ["DENEB sadr albireo", "delta_cyg sadr gienah_cyg"],
    "Lyra": ["VEGA zeta_lyr delta_lyr sulafat sheliak zeta_lyr"],
    "Aquila": ["zeta_aql tarazed ALTAIR alshain delta_aql lambda_aql", "zeta_aql delta_aql"],
    "Bootes": ["ARCTURUS muphrid", "ARCTURUS izar rho_boo seginus nekkar", "ARCTURUS rho_boo"],
    "Corvus": ["gienah_crv algorab kraz alchiba gienah_crv", "alchiba minkar"],
    "Ophiuchus": ["rasalhague cebalrai sabik zeta_oph yed_prior kappa_oph rasalhague"],
    "Crux": ["acrux gacrux", "mimosa imai"],
}
HOME_OF = {}
for _name, _lines in CONSTELLATIONS.items():
    for _line in _lines:
        for _star in _line.split():
            HOME_OF.setdefault(_star, _name)
PIXELS_PER_DEGREE = 3.0  # wide enough that whole constellations fit in the gap in the leaves
_TIMEZONE_FILES = ("/usr/share/zoneinfo/zone1970.tab", "/usr/share/zoneinfo/zone.tab")


def julian_day(when):
    """when must be timezone-aware."""
    moment = when.astimezone(datetime.timezone.utc)
    epoch = datetime.datetime(2000, 1, 1, 12, tzinfo=datetime.timezone.utc)
    return 2451545.0 + (moment - epoch).total_seconds() / 86400.0


def _sin(d):
    return math.sin(math.radians(d))


def _cos(d):
    return math.cos(math.radians(d))


def moon_ecliptic(jd):
    """Longitude, latitude and horizontal parallax in degrees, from the Astronomical Almanac's low-precision series."""
    t = (jd - 2451545.0) / 36525.0
    lon = (218.32 + 481267.881 * t + 6.29 * _sin(135.0 + 477198.87 * t) - 1.27 * _sin(259.3 - 413335.36 * t)
           + 0.66 * _sin(235.7 + 890534.22 * t) + 0.21 * _sin(269.9 + 954397.74 * t) - 0.19 * _sin(357.5 + 35999.05 * t)
           - 0.11 * _sin(186.5 + 966404.03 * t))
    lat = (5.13 * _sin(93.3 + 483202.02 * t) + 0.28 * _sin(228.2 + 960400.89 * t) - 0.28 * _sin(318.3 + 6003.15 * t)
           - 0.17 * _sin(217.6 - 407332.21 * t))
    parallax = (0.9508 + 0.0518 * _cos(135.0 + 477198.87 * t) + 0.0095 * _cos(259.3 - 413335.36 * t)
                + 0.0078 * _cos(235.7 + 890534.22 * t) + 0.0028 * _cos(269.9 + 954397.74 * t))
    return lon % 360.0, lat, parallax


def moon_equatorial(jd):
    """The moon's right ascension (degrees) and declination, in the same J2000 frame as the catalogue."""
    lon, lat, parallax = moon_ecliptic(jd)
    t = (jd - 2451545.0) / 36525.0
    lon -= 1.397 * t  # undo precession since 2000, so the moon and the stars agree
    obliquity = 23.439
    x = _cos(lat) * _cos(lon)
    y = _cos(obliquity) * _cos(lat) * _sin(lon) - _sin(obliquity) * _sin(lat)
    z = _sin(obliquity) * _cos(lat) * _sin(lon) + _cos(obliquity) * _sin(lat)
    return math.degrees(math.atan2(y, x)) % 360.0, math.degrees(math.asin(z)), parallax


def local_sidereal_degrees(jd, longitude):
    return (280.46061837 + 360.98564736629 * (jd - 2451545.0) + longitude) % 360.0


def to_horizon(ra, dec, lst, latitude):
    """A unit vector (east, north, up) for a position on the sky."""
    hour = lst - ra
    return (-_cos(dec) * _sin(hour),
            _sin(dec) * _cos(latitude) - _cos(dec) * _cos(hour) * _sin(latitude),
            _sin(dec) * _sin(latitude) + _cos(dec) * _cos(hour) * _cos(latitude))


def moon_vector(jd, lst, latitude):
    """Where the moon appears from where you stand, which is up to a degree from where it is from Earth's centre."""
    ra, dec, parallax = moon_equatorial(jd)
    e, n, u = to_horizon(ra, dec, lst, latitude)
    r = 1.0 / _sin(parallax)
    e, n, u = e * r, n * r, u * r - 1.0
    m = math.sqrt(e * e + n * n + u * u)
    return e / m, n / m, u / m


def location_from_timezone():
    """Your timezone's main city, from the system's own tables: only as exact as that, and entirely offline."""
    name = None
    try:
        with open("/etc/timezone") as f:
            name = f.read().strip()
    except OSError:
        try:
            name = os.path.realpath("/etc/localtime").split("zoneinfo/")[-1]
        except OSError:
            pass
    for path in _TIMEZONE_FILES:
        try:
            with open(path) as f:
                for line in f:
                    if line.startswith("#"):
                        continue
                    fields = line.split("\t")
                    if len(fields) > 2 and fields[2].strip() == name:
                        match = re.match(r"([+-])(\d{2})(\d{2})(\d*)([+-])(\d{3})(\d{2})(\d*)", fields[1])
                        if match:
                            lat = (int(match[2]) + int(match[3]) / 60.0) * (1 if match[1] == "+" else -1)
                            lon = (int(match[6]) + int(match[7]) / 60.0) * (1 if match[5] == "+" else -1)
                            return lat, lon, name
        except OSError:
            continue
    offset = datetime.datetime.now().astimezone().utcoffset()
    return 40.0, (offset.total_seconds() / 3600.0 if offset else 0.0) * 15.0, None


def parse_location(text):
    """'auto', or 'latitude, longitude' in degrees (north and east positive)."""
    if isinstance(text, str) and text.strip().lower() != "auto":
        try:
            lat, lon = (float(p) for p in text.split(","))
            if -90 <= lat <= 90 and -180 <= lon <= 180:
                return lat, lon, "set by you"
        except ValueError:
            pass
    return location_from_timezone()


class Sky:
    """Keeps the real stars painted into the sky, moving them as the night turns."""

    def __init__(self, eco):
        self.eco = eco
        self.saved = {}  # pixels we have changed, and what they were
        self.stars = []
        self.near = None
        self.visible = []
        self.place = None
        self.place_text = None

    def _place(self):
        text = self.eco.cfg.location
        if text != self.place_text:
            self.place_text = text
            self.place = parse_location(text)
        return self.place

    def project(self, when):
        """Screen positions of all catalogue stars for a moment, or None for ones too far from the moon."""
        latitude, longitude, _ = self._place()
        scene = self.eco.s
        moment = when if when.tzinfo else when.astimezone()
        jd = julian_day(moment)
        lst = local_sidereal_degrees(jd, longitude)
        c = moon_vector(jd, lst, latitude)
        # Facing the moon, with the zenith straight up.
        rx, ry, rz = c[1], -c[0], 0.0
        m = math.hypot(rx, ry) or 1.0
        rx, ry = rx / m, ry / m
        ux, uy, uz = ry * c[2] - rz * c[1], rz * c[0] - rx * c[2], rx * c[1] - ry * c[0]
        mx, my, _ = scene.moon
        scale = math.degrees(1.0) * PIXELS_PER_DEGREE * scene.h / 270.0
        positions = {}
        for name, (ra_hours, dec) in STARS.items():
            s = to_horizon(ra_hours * 15.0, dec, lst, latitude)
            depth = s[0] * c[0] + s[1] * c[1] + s[2] * c[2]
            if depth < 0.35:
                continue
            x = (s[0] * rx + s[1] * ry + s[2] * rz) / depth
            y = (s[0] * ux + s[1] * uy + s[2] * uz) / depth
            positions[name] = (mx + x * scale, my - y * scale, depth)
        return positions

    def update(self):
        """Repaint the stars for now. Only pixels that actually changed get touched."""
        eco = self.eco
        canvas = eco.c
        wanted = {}
        self.visible = []
        self.stars = []
        near_name, near_depth = None, -2.0
        if eco.cfg.constellations and eco.daylight < 0.3:
            when = eco.climate.now()
            positions = self.project(when)
            w = canvas.w
            gy = eco.gy
            base = canvas.base
            originals = self.saved
            sky = range(SKY.start, SKY.start + SKY.n)
            faint = range(STAR.start, STAR.start + STAR.n)

            def clear(i):
                v = originals.get(i, base[i])
                return v in sky or v in faint

            mx, my, mr = eco.s.moon
            showing = {}
            for name, (x, y, depth) in positions.items():
                if depth > near_depth:
                    near_name, near_depth = name, depth
                ix, iy = int(x), int(y)
                if 0 <= ix < w and 0 <= iy < gy and (ix - mx) ** 2 + (iy - my) ** 2 > (mr + 1) ** 2:
                    i = iy * w + ix
                    if clear(i):
                        wanted[i] = STAR[2]
                        self.stars.append((ix, iy))
                        home = HOME_OF.get(name)
                        showing[home] = showing.get(home, 0) + 1
                        # A little body of light around each, larger for the brightest: it should read as a star.
                        arms = (-1, 1, -w, w)
                        for off in arms:
                            k = i + off
                            if 0 <= k < w * gy and clear(k):
                                wanted[k] = STAR[1] if name.isupper() else STAR[0]
                        if name.isupper():
                            for off in (-2, 2, -2 * w, 2 * w):
                                k = i + off
                                if 0 <= k < w * gy and clear(k):
                                    wanted[k] = STAR[0]
            self.visible = [name for name, count in showing.items() if count >= 3]
        self.near = HOME_OF.get(near_name)
        # Put back what's no longer wanted, then paint what's new.
        for i in [i for i in self.saved if i not in wanted]:
            canvas.base[i] = self.saved.pop(i)
            canvas.refresh(i)
        for i, v in wanted.items():
            if i not in self.saved:
                self.saved[i] = canvas.base[i]
            if canvas.base[i] != v:
                canvas.base[i] = v
                canvas.refresh(i)

    def summary(self):
        place = self.place[2] if self.place else None
        return {"near": self.near, "visible": self.visible, "place": place}

