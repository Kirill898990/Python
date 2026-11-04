# Источник данных справочника: таблица Менделеева и таблица растворимости.
# Из него делаются файлы для сайта и для приложения, поэтому данные у них всегда одинаковые.
# Если нужно исправить клетку в таблице растворимости — исправьте её ниже (ROWS) и запустите из корня проекта:
#   python3 server/tools/make_reference.py server/pb_public/js/reference.js android/app/src/main/java/ru/prostayahimiya/app/chem/Reference.kt
import json, sys
E = """1 H Водород 1.008
2 He Гелий 4.0026
3 Li Литий 6.94
4 Be Бериллий 9.0122
5 B Бор 10.81
6 C Углерод 12.011
7 N Азот 14.007
8 O Кислород 15.999
9 F Фтор 18.998
10 Ne Неон 20.180
11 Na Натрий 22.990
12 Mg Магний 24.305
13 Al Алюминий 26.982
14 Si Кремний 28.085
15 P Фосфор 30.974
16 S Сера 32.06
17 Cl Хлор 35.45
18 Ar Аргон 39.948
19 K Калий 39.098
20 Ca Кальций 40.078
21 Sc Скандий 44.956
22 Ti Титан 47.867
23 V Ванадий 50.942
24 Cr Хром 51.996
25 Mn Марганец 54.938
26 Fe Железо 55.845
27 Co Кобальт 58.933
28 Ni Никель 58.693
29 Cu Медь 63.546
30 Zn Цинк 65.38
31 Ga Галлий 69.723
32 Ge Германий 72.630
33 As Мышьяк 74.922
34 Se Селен 78.971
35 Br Бром 79.904
36 Kr Криптон 83.798
37 Rb Рубидий 85.468
38 Sr Стронций 87.62
39 Y Иттрий 88.906
40 Zr Цирконий 91.224
41 Nb Ниобий 92.906
42 Mo Молибден 95.95
43 Tc Технеций [98]
44 Ru Рутений 101.07
45 Rh Родий 102.91
46 Pd Палладий 106.42
47 Ag Серебро 107.87
48 Cd Кадмий 112.41
49 In Индий 114.82
50 Sn Олово 118.71
51 Sb Сурьма 121.76
52 Te Теллур 127.60
53 I Иод 126.90
54 Xe Ксенон 131.29
55 Cs Цезий 132.91
56 Ba Барий 137.33
57 La Лантан 138.91
58 Ce Церий 140.12
59 Pr Празеодим 140.91
60 Nd Неодим 144.24
61 Pm Прометий [145]
62 Sm Самарий 150.36
63 Eu Европий 151.96
64 Gd Гадолиний 157.25
65 Tb Тербий 158.93
66 Dy Диспрозий 162.50
67 Ho Гольмий 164.93
68 Er Эрбий 167.26
69 Tm Тулий 168.93
70 Yb Иттербий 173.05
71 Lu Лютеций 174.97
72 Hf Гафний 178.49
73 Ta Тантал 180.95
74 W Вольфрам 183.84
75 Re Рений 186.21
76 Os Осмий 190.23
77 Ir Иридий 192.22
78 Pt Платина 195.08
79 Au Золото 196.97
80 Hg Ртуть 200.59
81 Tl Таллий 204.38
82 Pb Свинец 207.2
83 Bi Висмут 208.98
84 Po Полоний [209]
85 At Астат [210]
86 Rn Радон [222]
87 Fr Франций [223]
88 Ra Радий [226]
89 Ac Актиний [227]
90 Th Торий 232.04
91 Pa Протактиний 231.04
92 U Уран 238.03
93 Np Нептуний [237]
94 Pu Плутоний [244]
95 Am Америций [243]
96 Cm Кюрий [247]
97 Bk Берклий [247]
98 Cf Калифорний [251]
99 Es Эйнштейний [252]
100 Fm Фермий [257]
101 Md Менделевий [258]
102 No Нобелий [259]
103 Lr Лоуренсий [266]
104 Rf Резерфордий [267]
105 Db Дубний [268]
106 Sg Сиборгий [269]
107 Bh Борий [270]
108 Hs Хассий [269]
109 Mt Мейтнерий [278]
110 Ds Дармштадтий [281]
111 Rg Рентгений [282]
112 Cn Коперниций [285]
113 Nh Нихоний [286]
114 Fl Флеровий [289]
115 Mc Московий [290]
116 Lv Ливерморий [293]
117 Ts Теннессин [294]
118 Og Оганесон [294]"""

def position(z):
    """(строка, столбец) в таблице: строки 1–7 — периоды, 9 и 10 — лантаноиды и актиноиды."""
    if z == 1: return 1, 1
    if z == 2: return 1, 18
    bounds = [(3, 10, 2), (11, 18, 3)]
    for a, b, p in bounds:
        if a <= z <= b:
            i = z - a
            return p, (i + 1 if i < 2 else i + 11)
    if 19 <= z <= 36: return 4, z - 18
    if 37 <= z <= 54: return 5, z - 36
    for start, period, frow in [(55, 6, 9), (87, 7, 10)]:
        end = start + 31
        if start <= z <= end:
            i = z - start
            if i < 2: return period, i + 1
            if 2 <= i <= 16: return frow, i + 1        # La…Lu / Ac…Lr: отдельная строка, столбцы 3–17
            return period, i - 13                      # Hf (72) → 4 … Rn (86) → 18
    raise ValueError(z)

elements = []
for line in E.splitlines():
    z, sym, name, mass = line.split()
    z = int(z)
    row, col = position(z)
    block = "f" if row >= 9 else ("s" if col <= 2 or z == 2 else ("d" if col <= 12 else "p"))
    elements.append({"z": z, "symbol": sym, "name": name, "mass": mass, "row": row, "col": col, "block": block})
assert len(elements) == 118
seen = set()
for e in elements:
    key = (e["row"], e["col"]); assert key not in seen, e; seen.add(key)
assert next(e for e in elements if e["symbol"] == "Hf")["col"] == 4
assert next(e for e in elements if e["symbol"] == "Rn")["col"] == 18
assert next(e for e in elements if e["symbol"] == "Lu")["col"] == 17

CATIONS = ["H⁺", "Li⁺", "K⁺", "Na⁺", "NH₄⁺", "Ba²⁺", "Ca²⁺", "Mg²⁺", "Sr²⁺", "Al³⁺", "Cr³⁺", "Fe²⁺", "Fe³⁺", "Ni²⁺", "Co²⁺", "Mn²⁺", "Zn²⁺", "Ag⁺", "Hg²⁺", "Pb²⁺", "Sn²⁺", "Cu²⁺"]
ROWS = """OH⁻     — Р Р Р Р Р М Н М Н Н Н Н Н Н Н Н — — Н Н Н
Cl⁻     Р Р Р Р Р Р Р Р Р Р Р Р Р Р Р Р Р Н Р М Р Р
Br⁻     Р Р Р Р Р Р Р Р Р Р Р Р Р Р Р Р Р Н М М Р Р
I⁻      Р Р Р Р Р Р Р Р Р Р Р Р — Р Р Р Р Н Н М М —
S²⁻     Р Р Р Р Р Р М Р Р — — Н — Н Н Н Н Н Н Н Н Н
SO₃²⁻   Р Р Р Р Р Н Н М Н — — Н — Н Н Н Н Н Н Н — —
SO₄²⁻   Р Р Р Р Р Н М Р Н Р Р Р Р Р Р Р Р М — Н Р Р
NO₃⁻    Р Р Р Р Р Р Р Р Р Р Р Р Р Р Р Р Р Р Р Р — Р
PO₄³⁻   Р М Р Р Р Н Н Н Н Н Н Н Н Н Н Н Н Н Н Н Н Н
CO₃²⁻   Р Р Р Р Р Н Н М Н — — Н — Н Н Н Н Н — Н — —
SiO₃²⁻  Н Р Р Р — Н Н Н Н — — Н — — — Н Н — — Н — —
CH₃COO⁻ Р Р Р Р Р Р Р Р Р Р Р Р — Р Р Р Р М Р Р — Р"""
anions, cells = [], []
for line in ROWS.splitlines():
    parts = line.split()
    assert len(parts) == len(CATIONS) + 1, (parts[0], len(parts) - 1)
    anions.append(parts[0]); cells.append("".join(parts[1:]))
data = {"elements": elements, "solubility": {"cations": CATIONS, "anions": anions, "cells": cells}}

js = """/*
 * Справочник: таблица Менделеева и таблица растворимости.
 * Файл сделан автоматически из одного источника вместе с android/.../chem/Reference.kt — данные одинаковые.
 * Атомные массы — стандартные (в квадратных скобках — массовое число самого устойчивого изотопа).
 * Растворимость: Р — растворимо, М — малорастворимо, Н — нерастворимо, «—» — не существует или разлагается водой.
 */
(function () {
  "use strict";
  window.Reference = %s;
})();
""" % json.dumps(data, ensure_ascii=False, indent=2).replace("\n", "\n  ")
open(sys.argv[1], "w").write(js)

def kstr(s): return '"' + s.replace("\\", "\\\\").replace('"', '\\"') + '"'
kt = ["package ru.prostayahimiya.app.chem", "",
      "/*", " * Справочник: таблица Менделеева и таблица растворимости.",
      " * Файл сделан автоматически из одного источника вместе с server/pb_public/js/reference.js — данные одинаковые.",
      " * Атомные массы — стандартные (в квадратных скобках — массовое число самого устойчивого изотопа).",
      " */", "",
      "/** Элемент. row 1–7 — периоды, 9 и 10 — лантаноиды и актиноиды; col 1–18 — группы. block: s, p, d или f. */",
      "data class ElementInfo(val z: Int, val symbol: String, val name: String, val mass: String, val row: Int, val col: Int, val block: Char)", "",
      "object Reference {",
      "    val elements: List<ElementInfo> = listOf("]
for e in elements:
    kt.append(f"        ElementInfo({e['z']}, {kstr(e['symbol'])}, {kstr(e['name'])}, {kstr(e['mass'])}, {e['row']}, {e['col']}, '{e['block']}'),")
kt += ["    )", "",
       "    /** Растворимость: Р — растворимо, М — малорастворимо, Н — нерастворимо, «—» — не существует или разлагается водой. */",
       "    val cations: List<String> = listOf(" + ", ".join(kstr(c) for c in CATIONS) + ")",
       "    val anions: List<String> = listOf(" + ", ".join(kstr(a) for a in anions) + ")",
       "    /** Строка на каждый анион, по одному символу на катион в порядке [cations]. */",
       "    val solubility: List<String> = listOf(" + ", ".join(kstr(c) for c in cells) + ")",
       "}", ""]
open(sys.argv[2], "w").write("\n".join(kt))
print("ok", len(elements), "elements,", len(anions), "anions x", len(CATIONS), "cations")
