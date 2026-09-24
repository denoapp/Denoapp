package com.deno.social.data.mock

/*
 * Real-world country and primary subdivision (state / province / region)
 * dataset used by the Complete Profile selectors.
 */

object CountryData {

    /** All countries in alphabetical order. "Other" is a catch-all at the end. */
    val COUNTRIES: List<String> = listOf(
        "Afghanistan",
        "Albania",
        "Algeria",
        "Andorra",
        "Angola",
        "Antigua and Barbuda",
        "Argentina",
        "Armenia",
        "Australia",
        "Austria",
        "Azerbaijan",
        "Bahamas",
        "Bahrain",
        "Bangladesh",
        "Barbados",
        "Belarus",
        "Belgium",
        "Belize",
        "Benin",
        "Bhutan",
        "Bolivia",
        "Bosnia and Herzegovina",
        "Botswana",
        "Brazil",
        "Brunei",
        "Bulgaria",
        "Burkina Faso",
        "Burundi",
        "Cabo Verde",
        "Cambodia",
        "Cameroon",
        "Canada",
        "Central African Republic",
        "Chad",
        "Chile",
        "China",
        "Colombia",
        "Comoros",
        "Congo (Democratic Republic)",
        "Congo (Republic)",
        "Costa Rica",
        "Côte d'Ivoire",
        "Croatia",
        "Cuba",
        "Cyprus",
        "Czech Republic",
        "Denmark",
        "Djibouti",
        "Dominica",
        "Dominican Republic",
        "Ecuador",
        "Egypt",
        "El Salvador",
        "Equatorial Guinea",
        "Eritrea",
        "Estonia",
        "Eswatini",
        "Ethiopia",
        "Fiji",
        "Finland",
        "France",
        "Gabon",
        "Gambia",
        "Georgia",
        "Germany",
        "Ghana",
        "Greece",
        "Grenada",
        "Guatemala",
        "Guinea",
        "Guinea-Bissau",
        "Guyana",
        "Haiti",
        "Honduras",
        "Hungary",
        "Iceland",
        "India",
        "Indonesia",
        "Iran",
        "Iraq",
        "Ireland",
        "Israel",
        "Italy",
        "Jamaica",
        "Japan",
        "Jordan",
        "Kazakhstan",
        "Kenya",
        "Kiribati",
        "Korea (North)",
        "Korea (South)",
        "Kosovo",
        "Kuwait",
        "Kyrgyzstan",
        "Laos",
        "Latvia",
        "Lebanon",
        "Lesotho",
        "Liberia",
        "Libya",
        "Liechtenstein",
        "Lithuania",
        "Luxembourg",
        "Madagascar",
        "Malawi",
        "Malaysia",
        "Maldives",
        "Mali",
        "Malta",
        "Marshall Islands",
        "Mauritania",
        "Mauritius",
        "Mexico",
        "Micronesia",
        "Moldova",
        "Monaco",
        "Mongolia",
        "Montenegro",
        "Morocco",
        "Mozambique",
        "Myanmar",
        "Namibia",
        "Nauru",
        "Nepal",
        "Netherlands",
        "New Zealand",
        "Nicaragua",
        "Niger",
        "Nigeria",
        "North Macedonia",
        "Norway",
        "Oman",
        "Pakistan",
        "Palau",
        "Palestine",
        "Panama",
        "Papua New Guinea",
        "Paraguay",
        "Peru",
        "Philippines",
        "Poland",
        "Portugal",
        "Qatar",
        "Romania",
        "Russia",
        "Rwanda",
        "Saint Kitts and Nevis",
        "Saint Lucia",
        "Saint Vincent and the Grenadines",
        "Samoa",
        "San Marino",
        "São Tomé and Príncipe",
        "Saudi Arabia",
        "Senegal",
        "Serbia",
        "Seychelles",
        "Sierra Leone",
        "Singapore",
        "Slovakia",
        "Slovenia",
        "Solomon Islands",
        "Somalia",
        "South Africa",
        "South Sudan",
        "Spain",
        "Sri Lanka",
        "Sudan",
        "Suriname",
        "Sweden",
        "Switzerland",
        "Syria",
        "Taiwan",
        "Tajikistan",
        "Tanzania",
        "Thailand",
        "Timor-Leste",
        "Togo",
        "Tonga",
        "Trinidad and Tobago",
        "Tunisia",
        "Turkey",
        "Turkmenistan",
        "Tuvalu",
        "Uganda",
        "Ukraine",
        "United Arab Emirates",
        "United Kingdom",
        "United States",
        "Uruguay",
        "Uzbekistan",
        "Vanuatu",
        "Vatican City",
        "Venezuela",
        "Vietnam",
        "Yemen",
        "Zambia",
        "Zimbabwe",
        "Other"
    )

    /** Real primary subdivisions per country, sorted alphabetically with "Other" appended. */
    val REGIONS: Map<String, List<String>> = buildMap {
        put(
            "Afghanistan", listOf(
                "Badakhshan", "Badghis", "Baghlan", "Balkh", "Bamyan", "Daykundi",
                "Farah", "Faryab", "Ghazni", "Ghor", "Helmand", "Herat", "Jowzjan",
                "Kabul", "Kandahar", "Kapisa", "Khost", "Kunar", "Kunduz", "Laghman",
                "Logar", "Maidan Wardak", "Nangarhar", "Nimruz", "Nuristan", "Paktia",
                "Paktika", "Panjshir", "Parwan", "Samangan", "Sar-e Pol", "Takhar",
                "Uruzgan", "Zabul"
            )
        )
        put("Albania", listOf("Berat", "Dibër", "Durrës", "Elbasan", "Fier", "Gjirokastër", "Korçë", "Kukës", "Lezhë", "Shkodër", "Tirana", "Vlorë"))
        put(
            "Algeria", listOf(
                "Adrar", "Aïn Defla", "Aïn Témouchent", "Algiers", "Annaba", "Batna",
                "Béchar", "Béjaïa", "Biskra", "Blida", "Bordj Bou Arréridj", "Bouïra",
                "Boumerdès", "Chlef", "Constantine", "Djelfa", "El Bayadh", "El Oued",
                "El Tarf", "Ghardaïa", "Guelma", "Illizi", "Jijel", "Khenchela",
                "Laghouat", "Mascara", "Médéa", "Mila", "Mostaganem", "M'Sila",
                "Naâma", "Oran", "Ouargla", "Oum El Bouaghi", "Relizane", "Saïda",
                "Sétif", "Sidi Bel Abbès", "Skikda", "Souk Ahras", "Tamanrasset",
                "Tébessa", "Tiaret", "Tindouf", "Tipaza", "Tissemsilt", "Tizi Ouzou",
                "Tlemcen"
            )
        )
        put(
            "Andorra", listOf(
                "Andorra la Vella", "Canillo", "Encamp", "Escaldes-Engordany",
                "La Massana", "Ordino", "Sant Julià de Lòria"
            )
        )
        put(
            "Angola", listOf(
                "Bengo", "Benguela", "Bié", "Cabinda", "Cuando Cubango",
                "Cuanza Norte", "Cuanza Sul", "Cunene", "Huambo", "Huíla", "Luanda",
                "Lunda Norte", "Lunda Sul", "Malanje", "Moxico", "Namibe", "Uíge",
                "Zaire"
            )
        )
        put(
            "Antigua and Barbuda", listOf(
                "Barbuda", "Redonda", "Saint George", "Saint John", "Saint Mary",
                "Saint Paul", "Saint Peter", "Saint Philip"
            )
        )
        put(
            "Argentina", listOf(
                "Buenos Aires", "Buenos Aires Province", "Catamarca", "Chaco",
                "Chubut", "Córdoba", "Corrientes", "Entre Ríos", "Formosa", "Jujuy",
                "La Pampa", "La Rioja", "Mendoza", "Misiones", "Neuquén", "Río Negro",
                "Salta", "San Juan", "San Luis", "Santa Cruz", "Santa Fe",
                "Santiago del Estero", "Tierra del Fuego", "Tucumán"
            )
        )
        put(
            "Armenia", listOf(
                "Aragatsotn", "Ararat", "Armavir", "Gegharkunik", "Kotayk", "Lori",
                "Shirak", "Syunik", "Tavush", "Vayots Dzor", "Yerevan"
            )
        )
        put(
            "Australia", listOf(
                "Australian Capital Territory", "New South Wales", "Northern Territory",
                "Queensland", "South Australia", "Tasmania", "Victoria",
                "Western Australia"
            )
        )
        put(
            "Austria", listOf(
                "Burgenland", "Carinthia", "Lower Austria", "Salzburg", "Styria",
                "Tyrol", "Upper Austria", "Vorarlberg", "Vienna"
            )
        )
        put(
            "Azerbaijan", listOf(
                "Absheron", "Agdam", "Agjabadi", "Aghstafa", "Agsu", "Astara",
                "Baku", "Balakan", "Barda", "Beylagan", "Bilasuvar", "Dashkasan",
                "Fuzuli", "Ganja", "Gobustan", "Goychay", "Goygol", "Hajigabul",
                "Imishli", "Ismayilli", "Jabrayil", "Jalilabad", "Kalbajar",
                "Khachmaz", "Khirdalan", "Khojaly", "Kurdamir", "Lachin", "Lankaran",
                "Lerik", "Masally", "Mingachevir", "Naftalan", "Nakhchivan", "Neftchala",
                "Oghuz", "Ordubad", "Qabala", "Qakh", "Qazakh", "Quba", "Qubadli",
                "Qusar", "Saatly", "Sabirabad", "Shabran", "Shaki", "Shamakhi",
                "Shamkir", "Sharur", "Shirvan", "Shusha", "Siazan", "Sumqayit",
                "Tartar", "Tovuz", "Ujar", "Yardimli", "Yevlakh", "Zangilan",
                "Zaqatala", "Zardab"
            )
        )
        put(
            "Bahamas", listOf(
                "Acklins", "Berry Islands", "Bimini", "Black Point", "Cat Island",
                "Central Abaco", "Central Andros", "Central Eleuthera", "City of Freeport",
                "Crooked Island", "East Grand Bahama", "Exuma", "Grand Cay",
                "Harbour Island", "Hope Town", "Inagua", "Long Island", "Mangrove Cay",
                "Mayaguana", "Moore's Island", "New Providence", "North Abaco",
                "North Andros", "North Eleuthera", "Ragged Island", "Rum Cay",
                "San Salvador", "South Abaco", "South Andros", "South Eleuthera",
                "Spanish Wells", "West Grand Bahama"
            )
        )
        put(
            "Bahrain", listOf(
                "Capital Governorate", "Muharraq Governorate", "Northern Governorate",
                "Southern Governorate"
            )
        )
        put(
            "Bangladesh", listOf(
                "Barishal", "Chattogram", "Dhaka", "Khulna", "Mymensingh", "Rajshahi",
                "Rangpur", "Sylhet"
            )
        )
        put(
            "Barbados", listOf(
                "Christ Church", "Saint Andrew", "Saint George", "Saint James",
                "Saint John", "Saint Joseph", "Saint Lucy", "Saint Michael",
                "Saint Peter", "Saint Philip", "Saint Thomas"
            )
        )
        put(
            "Belarus", listOf(
                "Brest", "Gomel", "Grodno", "Minsk", "Minsk Region", "Mogilev",
                "Vitebsk"
            )
        )
        put(
            "Belgium", listOf(
                "Antwerp", "Brussels", "East Flanders", "Flemish Brabant", "Hainaut",
                "Liège", "Limburg", "Luxembourg", "Namur", "Walloon Brabant",
                "West Flanders"
            )
        )
        put(
            "Belize", listOf("Belize", "Cayo", "Corozal", "Orange Walk", "Stann Creek", "Toledo")
        )
        put(
            "Benin", listOf(
                "Alibori", "Atakora", "Atlantique", "Borgou", "Collines", "Couffo",
                "Donga", "Littoral", "Mono", "Ouémé", "Plateau", "Zou"
            )
        )
        put(
            "Bhutan", listOf(
                "Bumthang", "Chukha", "Dagana", "Gasa", "Haa", "Lhuntse", "Mongar",
                "Paro", "Pemagatshel", "Punakha", "Samdrup Jongkhar", "Samtse",
                "Sarpang", "Thimphu", "Trashigang", "Trashiyangtse", "Trongsa",
                "Tsirang", "Wangdue Phodrang", "Zhemgang"
            )
        )
        put(
            "Bolivia", listOf(
                "Beni", "Chuquisaca", "Cochabamba", "La Paz", "Oruro", "Pando",
                "Potosí", "Santa Cruz", "Tarija"
            )
        )
        put(
            "Bosnia and Herzegovina", listOf(
                "Brčko District", "Federation of Bosnia and Herzegovina",
                "Republika Srpska"
            )
        )
        put(
            "Botswana", listOf(
                "Central", "Chobe", "Francistown", "Gaborone", "Ghanzi", "Kgalagadi",
                "Kgatleng", "Kweneng", "Lobatse", "North East", "North West",
                "Selebi-Phikwe", "South East", "Southern"
            )
        )
        put(
            "Brazil", listOf(
                "Acre", "Alagoas", "Amapá", "Amazonas", "Bahia", "Ceará",
                "Distrito Federal", "Espírito Santo", "Goiás", "Maranhão", "Mato Grosso",
                "Mato Grosso do Sul", "Minas Gerais", "Pará", "Paraíba", "Paraná",
                "Pernambuco", "Piauí", "Rio de Janeiro", "Rio Grande do Norte",
                "Rio Grande do Sul", "Rondônia", "Roraima", "Santa Catarina",
                "São Paulo", "Sergipe", "Tocantins"
            )
        )
        put(
            "Brunei", listOf("Belait", "Brunei-Muara", "Temburong", "Tutong")
        )
        put(
            "Bulgaria", listOf(
                "Blagoevgrad", "Burgas", "Dobrich", "Gabrovo", "Haskovo", "Kardzhali",
                "Kyustendil", "Lovech", "Montana", "Pazardzhik", "Pernik", "Pleven",
                "Plovdiv", "Razgrad", "Ruse", "Shumen", "Silistra", "Sliven",
                "Smolyan", "Sofia", "Sofia Region", "Stara Zagora", "Targovishte",
                "Varna", "Veliko Tarnovo", "Vidin", "Vratsa", "Yambol"
            )
        )
        put(
            "Burkina Faso", listOf(
                "Boucle du Mouhoun", "Cascades", "Centre", "Centre-Est", "Centre-Nord",
                "Centre-Ouest", "Centre-Sud", "Est", "Hauts-Bassins", "Nord",
                "Plateau-Central", "Sahel", "Sud-Ouest"
            )
        )
        put(
            "Burundi", listOf(
                "Bubanza", "Bujumbura Mairie", "Bujumbura Rural", "Bururi", "Cankuzo",
                "Cibitoke", "Gitega", "Karuzi", "Kayanza", "Kirundo", "Makamba",
                "Muramvya", "Muyinga", "Mwaro", "Ngozi", "Rumonge", "Rutana", "Ruyigi"
            )
        )
        put(
            "Cabo Verde", listOf(
                "Boa Vista", "Brava", "Fogo", "Maio", "Sal", "Santiago", "Santo Antão",
                "São Nicolau", "São Vicente", "Tarrafal"
            )
        )
        put(
            "Cambodia", listOf(
                "Banteay Meanchey", "Battambang", "Kampong Cham", "Kampong Chhnang",
                "Kampong Speu", "Kampong Thom", "Kampot", "Kandal", "Kep", "Koh Kong",
                "Kratie", "Mondulkiri", "Oddar Meanchey", "Pailin", "Phnom Penh",
                "Preah Sihanouk", "Preah Vihear", "Prey Veng", "Pursat", "Ratanakiri",
                "Siem Reap", "Stung Treng", "Svay Rieng", "Takeo", "Tboung Khmum"
            )
        )
        put(
            "Cameroon", listOf(
                "Adamawa", "Centre", "East", "Far North", "Littoral", "North",
                "North West", "South", "South West", "West"
            )
        )
        put(
            "Canada", listOf(
                "Alberta", "British Columbia", "Manitoba", "New Brunswick",
                "Newfoundland and Labrador", "Northwest Territories", "Nova Scotia",
                "Nunavut", "Ontario", "Prince Edward Island", "Quebec",
                "Saskatchewan", "Yukon"
            )
        )
        put(
            "Central African Republic", listOf(
                "Bamingui-Bangoran", "Bangui", "Basse-Kotto", "Haute-Kotto",
                "Haut-Mbomou", "Kémo", "Lobaye", "Mambéré-Kadéï", "Mbomou",
                "Nana-Grébizi", "Nana-Mambéré", "Ombella-M'Poko", "Ouaka", "Ouham",
                "Ouham-Pendé", "Sangha-Mbaéré", "Vakaga"
            )
        )
        put(
            "Chad", listOf(
                "Barh el Gazel", "Batha", "Borkou", "Chari-Baguirmi", "Ennedi-Est",
                "Ennedi-Ouest", "Guéra", "Hadjer-Lamis", "Kanem", "Lac",
                "Logone Occidental", "Logone Oriental", "Mandoul", "Mayo-Kebbi Est",
                "Mayo-Kebbi Ouest", "Moyen-Chari", "N'Djamena", "Ouaddaï", "Salamat",
                "Sila", "Tandjilé", "Tibesti", "Wadi Fira"
            )
        )
        put(
            "Chile", listOf(
                "Arica y Parinacota", "Tarapacá", "Antofagasta", "Atacama", "Coquimbo",
                "Valparaíso", "Santiago Metropolitan", "O'Higgins", "Maule", "Ñuble",
                "Biobío", "Araucanía", "Los Ríos", "Los Lagos", "Aysén", "Magallanes"
            )
        )
        put(
            "China", listOf(
                "Anhui", "Beijing", "Chongqing", "Fujian", "Gansu", "Guangdong",
                "Guangxi", "Guizhou", "Hainan", "Hebei", "Heilongjiang", "Henan",
                "Hubei", "Hunan", "Inner Mongolia", "Jiangsu", "Jiangxi", "Jilin",
                "Liaoning", "Ningxia", "Qinghai", "Shaanxi", "Shandong", "Shanghai",
                "Shanxi", "Sichuan", "Tianjin", "Tibet", "Xinjiang", "Yunnan",
                "Zhejiang"
            )
        )
        put(
            "Colombia", listOf(
                "Amazonas", "Antioquia", "Arauca", "Atlántico", "Bogotá", "Bolívar",
                "Boyacá", "Caldas", "Caquetá", "Casanare", "Cauca", "Cesar", "Chocó",
                "Córdoba", "Cundinamarca", "Guainía", "Guaviare", "Huila",
                "La Guajira", "Magdalena", "Meta", "Nariño", "Norte de Santander",
                "Putumayo", "Quindío", "Risaralda", "San Andrés and Providencia",
                "Santander", "Sucre", "Tolima", "Valle del Cauca", "Vaupés", "Vichada"
            )
        )
        put("Comoros", listOf("Anjouan", "Grande Comore", "Mohéli"))
        put(
            "Congo (Democratic Republic)", listOf(
                "Bas-Uélé", "Équateur", "Haut-Katanga", "Haut-Lomami", "Haut-Uélé",
                "Ituri", "Kasai", "Kasai-Central", "Kasai-Oriental", "Kinshasa",
                "Kongo Central", "Kwango", "Kwilu", "Lomami", "Lualaba", "Mai-Ndombe",
                "Maniema", "Mongala", "Nord-Kivu", "Nord-Ubangi", "Sankuru",
                "South Kivu", "South Ubangi", "Tanganyika", "Tshopo", "Tshuapa"
            )
        )
        put(
            "Congo (Republic)", listOf(
                "Bouenza", "Brazzaville", "Cuvette", "Cuvette-Ouest", "Kouilou",
                "Lékoumou", "Likouala", "Niari", "Plateaux", "Pointe-Noire", "Pool",
                "Sangha"
            )
        )
        put(
            "Costa Rica", listOf(
                "Alajuela", "Cartago", "Guanacaste", "Heredia", "Limón", "Puntarenas",
                "San José"
            )
        )
        put(
            "Côte d'Ivoire", listOf(
                "Abidjan", "Bas-Sassandra", "Comoé", "Denguélé", "Gôh-Djiboua",
                "Lacs", "Lagunes", "Montagnes", "Sassandra-Marahoué", "Savanes",
                "Vallée du Bandama", "Woroba", "Yamoussoukro", "Zanzan"
            )
        )
        put(
            "Croatia", listOf(
                "Bjelovar-Bilogora", "Brod-Posavina", "Dubrovnik-Neretva", "Istria",
                "Karlovac", "Koprivnica-Križevci", "Krapina-Zagorje", "Lika-Senj",
                "Međimurje", "Osijek-Baranja", "Požega-Slavonia", "Primorje-Gorski Kotar",
                "Šibenik-Knin", "Sisak-Moslavina", "Split-Dalmatia", "Varaždin",
                "Virovitica-Podravina", "Vukovar-Srijem", "Zadar", "Zagreb",
                "Zagreb County"
            )
        )
        put(
            "Cuba", listOf(
                "Pinar del Río", "Artemisa", "La Habana", "Mayabeque", "Matanzas",
                "Cienfuegos", "Villa Clara", "Sancti Spíritus", "Ciego de Ávila",
                "Camagüey", "Las Tunas", "Holguín", "Granma", "Santiago de Cuba",
                "Guantánamo", "Isla de la Juventud"
            )
        )
        put(
            "Cyprus", listOf(
                "Famagusta", "Kyrenia", "Larnaca", "Limassol", "Nicosia", "Paphos"
            )
        )
        put(
            "Czech Republic", listOf(
                "Central Bohemia", "Hradec Králové", "Karlovy Vary", "Liberec",
                "Moravia-Silesia", "Olomouc", "Pardubice", "Plzeň", "Prague",
                "South Bohemia", "South Moravia", "Ústí nad Labem", "Vysočina", "Zlín"
            )
        )
        put(
            "Denmark", listOf(
                "Capital Region", "Central Denmark", "North Denmark", "Region Zealand",
                "Region of Southern Denmark"
            )
        )
        put(
            "Djibouti", listOf(
                "Ali Sabieh", "Arta", "Dikhil", "Djibouti City", "Obock", "Tadjourah"
            )
        )
        put(
            "Dominica", listOf(
                "Saint Andrew", "Saint David", "Saint George", "Saint John",
                "Saint Joseph", "Saint Luke", "Saint Mark", "Saint Patrick",
                "Saint Paul", "Saint Peter"
            )
        )
        put(
            "Dominican Republic", listOf(
                "Azua", "Baoruco", "Barahona", "Dajabón", "Distrito Nacional",
                "Duarte", "El Seibo", "Elías Piña", "Espaillat", "Hato Mayor",
                "Hermanas Mirabal", "Independencia", "La Altagracia", "La Romana",
                "La Vega", "María Trinidad Sánchez", "Monseñor Nouel", "Monte Cristi",
                "Monte Plata", "Pedernales", "Peravia", "Puerto Plata", "Samaná",
                "San Cristóbal", "San José de Ocoa", "San Juan", "San Pedro de Macorís",
                "Sánchez Ramírez", "Santiago", "Santiago Rodríguez", "Santo Domingo",
                "Valverde"
            )
        )
        put(
            "Ecuador", listOf(
                "Azuay", "Bolívar", "Cañar", "Carchi", "Chimborazo", "Cotopaxi",
                "El Oro", "Esmeraldas", "Galápagos", "Guayas", "Imbabura", "Loja",
                "Los Ríos", "Manabí", "Morona Santiago", "Napo", "Orellana", "Pastaza",
                "Pichincha", "Santa Elena", "Santo Domingo de los Tsáchilas", "Sucumbíos",
                "Tungurahua", "Zamora Chinchipe"
            )
        )
        put(
            "Egypt", listOf(
                "Alexandria", "Aswan", "Asyut", "Beheira", "Beni Suef", "Cairo",
                "Dakahlia", "Damietta", "Faiyum", "Gharbia", "Giza", "Ismailia",
                "Kafr El Sheikh", "Luxor", "Matrouh", "Minya", "Monufia",
                "New Valley", "North Sinai", "Port Said", "Qalyubia", "Qena",
                "Red Sea", "Sharqia", "Sohag", "South Sinai", "Suez"
            )
        )
        put(
            "El Salvador", listOf(
                "Ahuachapán", "Cabañas", "Chalatenango", "Cuscatlán", "La Libertad",
                "La Paz", "La Unión", "Morazán", "San Miguel", "San Salvador",
                "San Vicente", "Santa Ana", "Sonsonate", "Usulután"
            )
        )
        put(
            "Equatorial Guinea", listOf(
                "Annobón", "Bioko Norte", "Bioko Sur", "Centro Sur", "Djibloho",
                "Kié-Ntem", "Litoral", "Wele-Nzas"
            )
        )
        put(
            "Eritrea", listOf(
                "Anseba", "Debub", "Debub-Keih Bahri", "Gash-Barka", "Maekel",
                "Northern Red Sea", "Southern Red Sea"
            )
        )
        put(
            "Estonia", listOf(
                "Harju", "Hiiu", "Ida-Viru", "Järva", "Jõgeva", "Lääne", "Lääne-Viru",
                "Pärnu", "Põlva", "Rapla", "Saare", "Tartu", "Valga", "Viljandi",
                "Võru"
            )
        )
        put(
            "Eswatini", listOf("Hhohho", "Lubombo", "Manzini", "Shiselweni")
        )
        put(
            "Ethiopia", listOf(
                "Addis Ababa", "Afar", "Amhara", "Benishangul-Gumuz", "Dire Dawa",
                "Gambela", "Harari", "Oromia", "Sidama", "Somali",
                "South West Ethiopia", "Southern Nations", "Tigray"
            )
        )
        put(
            "Fiji", listOf(
                "Ba", "Bua", "Cakaudrove", "Central Division", "Eastern Division",
                "Kadavu", "Lau", "Lomaiviti", "Macuata", "Nadroga-Navosa", "Naitasiri",
                "Namosi", "Northern Division", "Ra", "Rewa", "Rotuma", "Serua",
                "Tailevu", "Western Division"
            )
        )
        put(
            "Finland", listOf(
                "Åland", "Central Finland", "Central Ostrobothnia", "Kainuu",
                "Kymenlaakso", "Lapland", "North Karelia", "North Ostrobothnia",
                "North Savo", "Ostrobothnia", "Päijät-Häme", "Pirkanmaa", "Satakunta",
                "South Karelia", "South Ostrobothnia", "South Savo", "Southwest Finland",
                "Uusimaa"
            )
        )
        put(
            "France", listOf(
                "Auvergne-Rhône-Alpes", "Bourgogne-Franche-Comté", "Brittany",
                "Centre-Val de Loire", "Corsica", "Grand Est", "Hauts-de-France",
                "Île-de-France", "Normandy", "Nouvelle-Aquitaine", "Occitanie",
                "Pays de la Loire", "Provence-Alpes-Côte d'Azur", "French Guiana",
                "Guadeloupe", "Martinique", "Mayotte", "Réunion"
            )
        )
        put(
            "Gabon", listOf(
                "Estuaire", "Haut-Ogooué", "Moyen-Ogooué", "Ngounié", "Nyanga",
                "Ogooué-Ivindo", "Ogooué-Lolo", "Ogooué-Maritime", "Woleu-Ntem"
            )
        )
        put(
            "Gambia", listOf(
                "Banjul", "Central River", "Lower River", "North Bank", "Upper River",
                "West Coast"
            )
        )
        put(
            "Georgia", listOf(
                "Adjara", "Guria", "Imereti", "Kakheti", "Kvemo Kartli",
                "Mtskheta-Mtianeti", "Racha-Lechkhumi and Kvemo Svaneti",
                "Samegrelo-Zemo Svaneti", "Samtskhe-Javakheti", "Shida Kartli",
                "Tbilisi"
            )
        )
        put(
            "Germany", listOf(
                "Baden-Württemberg", "Bavaria", "Berlin", "Brandenburg", "Bremen",
                "Hamburg", "Hesse", "Lower Saxony", "Mecklenburg-Vorpommern",
                "North Rhine-Westphalia", "Rhineland-Palatinate", "Saarland", "Saxony",
                "Saxony-Anhalt", "Schleswig-Holstein", "Thuringia"
            )
        )
        put(
            "Ghana", listOf(
                "Ahafo", "Ashanti", "Bono", "Bono East", "Central", "Eastern",
                "Greater Accra", "North East", "Northern", "Oti", "Savannah", "Upper East",
                "Upper West", "Volta", "Western", "Western North"
            )
        )
        put(
            "Greece", listOf(
                "Attica", "Central Greece", "Central Macedonia", "Crete",
                "Eastern Macedonia and Thrace", "Epirus", "Ionian Islands",
                "North Aegean", "Peloponnese", "South Aegean", "Thessaly",
                "Western Greece", "Western Macedonia"
            )
        )
        put(
            "Grenada", listOf(
                "Saint Andrew", "Saint David", "Saint George", "Saint John",
                "Saint Mark", "Saint Patrick"
            )
        )
        put(
            "Guatemala", listOf(
                "Alta Verapaz", "Baja Verapaz", "Chimaltenango", "Chiquimula",
                "El Progreso", "Escuintla", "Guatemala", "Huehuetenango", "Izabal",
                "Jalapa", "Jutiapa", "Petén", "Quetzaltenango", "Quiché",
                "Retalhuleu", "Sacatepéquez", "San Marcos", "Santa Rosa", "Sololá",
                "Suchitepéquez", "Totonicapán", "Zacapa"
            )
        )
        put(
            "Guinea", listOf(
                "Boké", "Conakry", "Faranah", "Kankan", "Kindia", "Labé", "Mamou",
                "Nzérékoré"
            )
        )
        put(
            "Guinea-Bissau", listOf(
                "Bafatá", "Biombo", "Bissau", "Bolama", "Cacheu", "Gabú", "Oio",
                "Quinara", "Tombali"
            )
        )
        put(
            "Guyana", listOf(
                "Barima-Waini", "Cuyuni-Mazaruni", "Demerara-Mahaica",
                "East Berbice-Corentyne", "Essequibo Islands-West Demerara",
                "Mahaica-Berbice", "Pomeroon-Supenaam", "Potaro-Siparuni",
                "Upper Demerara-Berbice", "Upper Takutu-Upper Essequibo"
            )
        )
        put(
            "Haiti", listOf(
                "Artibonite", "Centre", "Grand'Anse", "Nippes", "Nord", "Nord-Est",
                "Nord-Ouest", "Ouest", "Sud", "Sud-Est"
            )
        )
        put(
            "Honduras", listOf(
                "Atlántida", "Choluteca", "Colón", "Comayagua", "Copán", "Cortés",
                "El Paraíso", "Francisco Morazán", "Gracias a Dios", "Intibucá",
                "Islas de la Bahía", "La Paz", "Lempira", "Ocotepeque", "Olancho",
                "Santa Bárbara", "Valle", "Yoro"
            )
        )
        put(
            "Hungary", listOf(
                "Bács-Kiskun", "Baranya", "Békés", "Borsod-Abaúj-Zemplén", "Budapest",
                "Csongrád-Csanád", "Fejér", "Győr-Moson-Sopron", "Hajdú-Bihar", "Heves",
                "Jász-Nagykun-Szolnok", "Komárom-Esztergom", "Nógrád", "Pest", "Somogy",
                "Szabolcs-Szatmár-Bereg", "Tolna", "Vas", "Veszprém", "Zala"
            )
        )
        put(
            "Iceland", listOf(
                "Capital Region", "Eastern Region", "Northeastern Region",
                "Northwestern Region", "Southern Peninsula", "Southern Region",
                "Western Region", "Westfjords"
            )
        )
        put(
            "India", listOf(
                "Andaman and Nicobar Islands", "Andhra Pradesh", "Arunachal Pradesh",
                "Assam", "Bihar", "Chandigarh", "Chhattisgarh", "Dadra and Nagar Haveli and Daman and Diu",
                "Delhi", "Goa", "Gujarat", "Haryana", "Himachal Pradesh",
                "Jammu and Kashmir", "Jharkhand", "Karnataka", "Kerala", "Ladakh",
                "Lakshadweep", "Madhya Pradesh", "Maharashtra", "Manipur", "Meghalaya",
                "Mizoram", "Nagaland", "Odisha", "Puducherry", "Punjab", "Rajasthan",
                "Sikkim", "Tamil Nadu", "Telangana", "Tripura", "Uttar Pradesh",
                "Uttarakhand", "West Bengal"
            )
        )
        put(
            "Indonesia", listOf(
                "Aceh", "Bali", "Bangka Belitung", "Banten", "Bengkulu", "Central Java",
                "Central Kalimantan", "Central Sulawesi", "East Java", "East Kalimantan",
                "East Nusa Tenggara", "Gorontalo", "Jakarta", "Jambi", "Lampung",
                "Maluku", "North Kalimantan", "North Maluku", "North Sulawesi",
                "North Sumatra", "Papua", "Riau", "Riau Islands", "South Kalimantan",
                "South Sulawesi", "South Sumatra", "Southeast Sulawesi", "Special Region of Yogyakarta",
                "West Java", "West Kalimantan", "West Nusa Tenggara", "West Papua",
                "West Sulawesi", "West Sumatra"
            )
        )
        put(
            "Iran", listOf(
                "Alborz", "Ardabil", "Bushehr", "Chaharmahal and Bakhtiari",
                "East Azerbaijan", "Fars", "Gilan", "Golestan", "Hamadan", "Hormozgan",
                "Ilam", "Isfahan", "Kerman", "Kermanshah", "Khuzestan",
                "Kohgiluyeh and Boyer-Ahmad", "Kurdistan", "Lorestan", "Markazi",
                "Mazandaran", "North Khorasan", "Qazvin", "Qom", "Razavi Khorasan",
                "Semnan", "Sistan and Baluchestan", "South Khorasan", "Tehran",
                "West Azerbaijan", "Yazd", "Zanjan"
            )
        )
        put(
            "Iraq", listOf(
                "Al Anbar", "Al-Qadisiyyah", "Babylon", "Baghdad", "Basra", "Dhi Qar",
                "Diyala", "Dohuk", "Erbil", "Karbala", "Kirkuk", "Maysan", "Muthanna",
                "Najaf", "Nineveh", "Saladin", "Sulaymaniyah", "Wasit"
            )
        )
        put(
            "Ireland", listOf(
                "Carlow", "Cavan", "Clare", "Cork", "Donegal", "Dublin", "Galway",
                "Kerry", "Kildare", "Kilkenny", "Laois", "Leitrim", "Limerick",
                "Longford", "Louth", "Mayo", "Meath", "Monaghan", "Offaly",
                "Roscommon", "Sligo", "Tipperary", "Waterford", "Westmeath",
                "Wexford", "Wicklow"
            )
        )
        put(
            "Israel", listOf(
                "Central District", "Haifa District", "Jerusalem District",
                "Northern District", "Southern District", "Tel Aviv District"
            )
        )
        put(
            "Italy", listOf(
                "Abruzzo", "Aosta Valley", "Apulia", "Basilicata", "Calabria",
                "Campania", "Emilia-Romagna", "Friuli-Venezia Giulia", "Lazio",
                "Liguria", "Lombardy", "Marche", "Molise", "Piedmont", "Sardinia",
                "Sicily", "Trentino-Alto Adige", "Tuscany", "Umbria", "Veneto"
            )
        )
        put(
            "Jamaica", listOf(
                "Clarendon", "Hanover", "Kingston", "Manchester", "Portland",
                "Saint Andrew", "Saint Ann", "Saint Catherine", "Saint Elizabeth",
                "Saint James", "Saint Mary", "Saint Thomas", "Trelawny", "Westmoreland"
            )
        )
        put(
            "Japan", listOf(
                "Aichi", "Akita", "Aomori", "Chiba", "Ehime", "Fukui", "Fukuoka",
                "Fukushima", "Gifu", "Gunma", "Hiroshima", "Hokkaido", "Hyogo",
                "Ibaraki", "Ishikawa", "Iwate", "Kagawa", "Kagoshima", "Kanagawa",
                "Koichi", "Kumamoto", "Kyoto", "Mie", "Miyagi", "Miyazaki", "Nagano",
                "Nagasaki", "Nara", "Niigata", "Oita", "Okayama", "Okinawa", "Osaka",
                "Saga", "Saitama", "Shiga", "Shimane", "Shizuoka", "Tochigi",
                "Tokushima", "Tokyo", "Tottori", "Toyama", "Wakayama", "Yamagata",
                "Yamaguchi", "Yamanashi"
            )
        )
        put(
            "Jordan", listOf(
                "Ajloun", "Amman", "Aqaba", "Balqa", "Irbid", "Jerash", "Karak",
                "Ma'an", "Madaba", "Mafraq", "Tafilah", "Zarqa"
            )
        )
        put(
            "Kazakhstan", listOf(
                "Akmola", "Aktobe", "Almaty", "Almaty Region", "Astana", "Atyrau",
                "East Kazakhstan", "Jambyl", "Karaganda", "Kostanay", "Kyzylorda",
                "Mangystau", "North Kazakhstan", "Pavlodar", "Shymkent", "Turkistan",
                "West Kazakhstan"
            )
        )
        put(
            "Kenya", listOf(
                "Baringo", "Bomet", "Bungoma", "Busia", "Elgeyo-Marakwet", "Embu",
                "Garissa", "Homa Bay", "Isiolo", "Kajiado", "Kakamega", "Kericho",
                "Kiambu", "Kilifi", "Kirinyaga", "Kisii", "Kisumu", "Kitui", "Kwale",
                "Laikipia", "Lamu", "Machakos", "Makueni", "Mandera", "Marsabit",
                "Meru", "Migori", "Mombasa", "Murang'a", "Nairobi", "Nakuru", "Nandi",
                "Narok", "Nyamira", "Nyandarua", "Nyeri", "Samburu", "Siaya",
                "Taita-Taveta", "Tana River", "Tharaka-Nithi", "Trans-Nzoia", "Turkana",
                "Uasin Gishu", "Vihiga", "Wajir", "West Pokot"
            )
        )
        put(
            "Kiribati", listOf(
                "Abaiang", "Abemama", "Aranuka", "Arorae", "Banaba", "Beru", "Butaritari",
                "Central Gilbert Islands", "Line Islands", "Maiana", "Makin", "Marakei",
                "Nikunau", "Nonouti", "Onotoa", "Phoenix Islands", "Tabiteuea",
                "Tabuaeran", "Tamana", "Tarawa", "Teraina"
            )
        )
        put("Korea (North)", listOf("Chagang", "Hamgyong-Namdo", "Hamgyong-Pukto", "Hwanghae-Namdo", "Hwanghae-Pukto", "Kangwon", "Najin-Seonbong", "P'yongan-Namdo", "P'yongan-Pukto", "Pyongyang", "Yanggang"))
        put(
            "Korea (South)", listOf(
                "Busan", "Chungcheongbuk-do", "Chungcheongnam-do", "Daegu", "Daejeon",
                "Gangwon-do", "Gwangju", "Gyeonggi-do", "Gyeongsangbuk-do",
                "Gyeongsangnam-do", "Incheon", "Jeju-do", "Jeollabuk-do",
                "Jeollanam-do", "Sejong", "Seoul", "Ulsan"
            )
        )
        put(
            "Kosovo", listOf(
                "Đakovica", "Gjilan", "Kamenica", "Lipjan", "Mitrovica", "Peja",
                "Pristina", "Prizren", "Rahovec", "Ferizaj", "Suhareka"
            )
        )
        put(
            "Kuwait", listOf(
                "Al Ahmadi", "Al Asimah", "Al Farwaniyah", "Al Jahra", "Hawalli",
                "Mubarak Al-Kabeer"
            )
        )
        put(
            "Kyrgyzstan", listOf(
                "Batken", "Bishkek", "Chüy", "Issyk-Kul", "Jalal-Abad", "Naryn",
                "Osh", "Osh Region", "Talas"
            )
        )
        put(
            "Laos", listOf(
                "Attapeu", "Bokeo", "Bolikhamsai", "Champasak", "Houaphanh",
                "Khammouane", "Luang Namtha", "Luang Prabang", "Oudomxay",
                "Phongsaly", "Salavan", "Savannakhet", "Sekong", "Vientiane",
                "Vientiane Prefecture", "Xaisomboun", "Sayaboury", "Xieng Khouang"
            )
        )
        put(
            "Latvia", listOf(
                "Kurzeme", "Latgale", "Riga", "Riga Region", "Vidzeme", "Zemgale"
            )
        )
        put(
            "Lebanon", listOf(
                "Akkar", "Baalbek-Hermel", "Beirut", "Beqaa", "Keserwan-Jbeil",
                "Mount Lebanon", "Nabatieh", "North", "South"
            )
        )
        put(
            "Lesotho", listOf(
                "Berea", "Butha-Buthe", "Leribe", "Mafeteng", "Maseru",
                "Mohale's Hoek", "Mokhotlong", "Qacha's Nek", "Quthing", "Thaba-Tseka"
            )
        )
        put(
            "Liberia", listOf(
                "Bomi", "Bong", "Gbarpolu", "Grand Bassa", "Grand Cape Mount",
                "Grand Gedeh", "Grand Kru", "Lofa", "Margibi", "Maryland",
                "Montserrado", "Nimba", "River Cess", "River Gee", "Sinoe"
            )
        )
        put(
            "Libya", listOf(
                "Al Wahat", "Benghazi", "Derna", "Ghat", "Jabal al Akhdar",
                "Jabal al Gharbi", "Jafara", "Jufra", "Kufra", "Marj", "Misrata",
                "Murqub", "Murzuq", "Nalut", "Nuqat al Khams", "Sabha", "Sirte",
                "Tripoli", "Wadi al Hayaa", "Wadi al Shatii", "Zawiya"
            )
        )
        put(
            "Liechtenstein", listOf(
                "Balzers", "Eschen", "Gamprin", "Mauren", "Planken", "Ruggell",
                "Schaan", "Schellenberg", "Triesen", "Triesenberg", "Vaduz"
            )
        )
        put(
            "Lithuania", listOf(
                "Alytus", "Kaunas", "Klaipėda", "Marijampolė", "Panevėžys",
                "Šiauliai", "Tauragė", "Telšiai", "Utena", "Vilnius"
            )
        )
        put(
            "Luxembourg", listOf(
                "Capellen", "Clervaux", "Diekirch", "Echternach", "Esch-sur-Alzette",
                "Grevenmacher", "Luxembourg", "Mersch", "Redange", "Remich", "Vianden",
                "Wiltz"
            )
        )
        put(
            "Madagascar", listOf(
                "Antananarivo", "Antsiranana", "Fianarantsoa", "Mahajanga",
                "Toamasina", "Toliara"
            )
        )
        put(
            "Malawi", listOf(
                "Balaka", "Blantyre", "Chikwawa", "Chiradzulu", "Chitipa", "Dedza",
                "Dowa", "Karonga", "Kasungu", "Likoma", "Lilongwe", "Machinga",
                "Mangochi", "Mchinji", "Mulanje", "Mwanza", "Mzimba", "Neno",
                "Nkhata Bay", "Nkhotakota", "Nsanje", "Ntcheu", "Ntchisi", "Phalombe",
                "Rumphi", "Salima", "Thyolo", "Zomba"
            )
        )
        put(
            "Malaysia", listOf(
                "Johor", "Kedah", "Kelantan", "Kuala Lumpur", "Labuan", "Malacca",
                "Negeri Sembilan", "Pahang", "Penang", "Perak", "Perlis", "Putrajaya",
                "Sabah", "Sarawak", "Selangor", "Terengganu"
            )
        )
        put(
            "Maldives", listOf(
                "Addu City", "Alif Alif Atoll", "Alif Dhaal Atoll", "Baa Atoll",
                "Dhaalu Atoll", "Faafu Atoll", "Fuvahmulah", "Gaafu Alif Atoll",
                "Gaafu Dhaal Atoll", "Gnaviyani Atoll", "Haa Alif Atoll",
                "Haa Dhaal Atoll", "Hulhumale", "Kaafu Atoll", "Kulhudhuffushi",
                "Lhaviyani Atoll", "Malé", "Meemu Atoll", "Noonu Atoll",
                "Raa Atoll", "Seenu Atoll", "Shaviyani Atoll", "Thaa Atoll",
                "Vaavu Atoll"
            )
        )
        put(
            "Mali", listOf(
                "Bamako", "Gao", "Kayes", "Kidal", "Koulikoro", "Ménaka", "Mopti",
                "Ségou", "Sikasso", "Taoudénit", "Tombouctou"
            )
        )
        put(
            "Malta", listOf("Gozo", "Northern", "Southeastern", "Southern", "Western")
        )
        put(
            "Marshall Islands", listOf(
                "Ailinglaplap", "Ebon", "Enewetak", "Jaluit", "Kili", "Kwajalein",
                "Lae", "Lib", "Likiep", "Majuro", "Maloelap", "Mejit", "Mili",
                "Namdrik", "Namu", "Rongelap", "Ujae", "Utirik", "Wotho", "Wotje"
            )
        )
        put(
            "Mauritania", listOf(
                "Adrar", "Assaba", "Brakna", "Dakhlet Nouadhibou", "Gorgol",
                "Guidimaka", "Hodh Ech Chargui", "Hodh El Gharbi", "Inchiri",
                "Nouakchott", "Tagant", "Tiris Zemmour", "Trarza"
            )
        )
        put(
            "Mauritius", listOf(
                "Black River", "Flacq", "Grand Port", "Moka", "Pamplemousses",
                "Plaines Wilhems", "Port Louis", "Rivière du Rempart", "Rodrigues",
                "Savanne"
            )
        )
        put(
            "Mexico", listOf(
                "Aguascalientes", "Baja California", "Baja California Sur", "Campeche",
                "Chiapas", "Chihuahua", "Coahuila", "Colima", "Durango", "Mexico City",
                "Guanajuato", "Guerrero", "Hidalgo", "Jalisco", "Mexico State",
                "Michoacán", "Morelos", "Nayarit", "Nuevo León", "Oaxaca", "Puebla",
                "Querétaro", "Quintana Roo", "San Luis Potosí", "Sinaloa", "Sonora",
                "Tabasco", "Tamaulipas", "Tlaxcala", "Veracruz", "Yucatán", "Zacatecas"
            )
        )
        put(
            "Micronesia", listOf(
                "Chuuk", "Kosrae", "Pohnpei", "Yap"
            )
        )
        put(
            "Moldova", listOf(
                "Anenii Noi", "Bălți", "Basarabeasca", "Briceni", "Cahul", "Cantemir",
                "Călărași", "Căușeni", "Chișinău", "Cimișlia", "Criuleni",
                "Dondușeni", "Drochia", "Dubăsari", "Edineț", "Fălești", "Florești",
                "Gagauzia", "Glodeni", "Hîncești", "Ialoveni", "Leova", "Nisporeni",
                "Ocnița", "Orhei", "Rezina", "Rîșcani", "Sîngerei", "Soroca",
                "Strășeni", "Șoldănești", "Ștefan Vodă", "Taraclia", "Telenești",
                "Transnistria", "Ungheni"
            )
        )
        put(
            "Monaco", listOf("Monaco")
        )
        put(
            "Mongolia", listOf(
                "Arkhangai", "Bayan-Ölgii", "Bayankhongor", "Bulgan",
                "Darkhan-Uul", "Dornod", "Dornogovi", "Dundgovi", "Govi-Altai",
                "Govisümber", "Khentii", "Khovd", "Khövsgöl", "Ömnögovi", "Orkhon",
                "Övörkhangai", "Selenge", "Sükhbaatar", "Töv", "Ulaanbaatar", "Uvs",
                "Zavkhan"
            )
        )
        put(
            "Montenegro", listOf(
                "Andrijevica", "Bar", "Berane", "Bijelo Polje", "Budva", "Cetinje",
                "Danilovgrad", "Gusinje", "Herceg Novi", "Kolašin", "Kotor",
                "Mojkovac", "Nikšić", "Petnjica", "Plav", "Plužine", "Pljevlja",
                "Podgorica", "Rožaje", "Šavnik", "Tivat", "Ulcinj", "Žabljak"
            )
        )
        put(
            "Morocco", listOf(
                "Béni Mellal-Khénifra", "Casablanca-Settat", "Dakhla-Oued Ed-Dahab",
                "Drâa-Tafilalet", "Fès-Meknès", "Guelmim-Oued Noun",
                "Laâyoune-Sakia El Hamra", "Marrakech-Safi", "Oriental",
                "Rabat-Salé-Kénitra", "Souss-Massa", "Tanger-Tétouan-Al Hoceïma"
            )
        )
        put(
            "Mozambique", listOf(
                "Cabo Delgado", "Gaza", "Inhambane", "Manica", "Maputo",
                "Maputo City", "Nampula", "Niassa", "Sofala", "Tete", "Zambezia"
            )
        )
        put(
            "Myanmar", listOf(
                "Ayeyarwady", "Bago", "Chin", "Kachin", "Kayah", "Kayin", "Magway",
                "Mandalay", "Mon", "Naypyidaw", "Rakhine", "Sagaing", "Shan",
                "Tanintharyi", "Yangon"
            )
        )
        put(
            "Namibia", listOf(
                "Erongo", "Hardap", "Karas", "Kavango East", "Kavango West",
                "Khomas", "Kunene", "Ohangwena", "Omaheke", "Omusati", "Oshana",
                "Oshikoto", "Otjozondjupa", "Zambezi"
            )
        )
        put("Nauru", listOf("Nauru"))
        put(
            "Nepal", listOf(
                "Bagmati", "Gandaki", "Karnali", "Koshi", "Lumbini", "Madhesh",
                "Sudurpashchim"
            )
        )
        put(
            "Netherlands", listOf(
                "Drenthe", "Flevoland", "Friesland", "Gelderland", "Groningen",
                "Limburg", "North Brabant", "North Holland", "Overijssel",
                "South Holland", "Utrecht", "Zeeland"
            )
        )
        put(
            "New Zealand", listOf(
                "Auckland", "Bay of Plenty", "Canterbury", "Chatham Islands",
                "Gisborne", "Hawke's Bay", "Manawatū-Whanganui", "Marlborough",
                "Nelson", "Northland", "Otago", "Southland", "Taranaki", "Tasman",
                "Waikato", "Wellington", "West Coast"
            )
        )
        put(
            "Nicaragua", listOf(
                "Boaco", "Carazo", "Chinandega", "Chontales", "Estelí", "Granada",
                "Jinotega", "León", "Madriz", "Managua", "Masaya", "Matagalpa",
                "Nueva Segovia", "Rivas", "Río San Juan", "North Caribbean Coast",
                "South Caribbean Coast"
            )
        )
        put(
            "Niger", listOf(
                "Agadez", "Diffa", "Dosso", "Maradi", "Niamey", "Tahoua", "Tillabéri",
                "Zinder"
            )
        )
        put(
            "Nigeria", listOf(
                "Abia", "Adamawa", "Akwa Ibom", "Anambra", "Bauchi", "Bayelsa",
                "Benue", "Borno", "Cross River", "Delta", "Ebonyi", "Edo", "Ekiti",
                "Enugu", "Federal Capital Territory", "Gombe", "Imo", "Jigawa",
                "Kaduna", "Kano", "Katsina", "Kebbi", "Kogi", "Kwara", "Lagos",
                "Nasarawa", "Niger", "Ogun", "Ondo", "Osun", "Oyo", "Plateau", "Rivers",
                "Sokoto", "Taraba", "Yobe", "Zamfara"
            )
        )
        put(
            "North Macedonia", listOf(
                "Eastern", "Northeastern", "Pelagonia", "Polog", "Skopje",
                "Southeastern", "Southwestern", "Vardar"
            )
        )
        put(
            "Norway", listOf(
                "Akershus", "Aust-Agder", "Buskerud", "Finnmark", "Hedmark",
                "Hordaland", "Møre og Romsdal", "Nordland", "Nord-Trøndelag",
                "Oppland", "Oslo", "Rogaland", "Sogn og Fjordane", "Sør-Trøndelag",
                "Telemark", "Troms", "Vest-Agder", "Vestfold", "Østfold"
            )
        )
        put(
            "Oman", listOf(
                "Ad Dakhiliyah", "Al Buraymi", "Al Dhahirah", "Al Wusta", "Dhofar",
                "Musandam", "Muscat", "North Al Batinah", "North Ash Sharqiyah",
                "South Al Batinah", "South Ash Sharqiyah"
            )
        )
        put(
            "Pakistan", listOf(
                "Azad Kashmir", "Balochistan", "Gilgit-Baltistan",
                "Islamabad Capital Territory", "Khyber Pakhtunkhwa", "Punjab", "Sindh"
            )
        )
        put(
            "Palau", listOf(
                "Aimeliik", "Airai", "Angaur", "Hatohobei", "Kayangel", "Koror",
                "Melekeok", "Ngaraard", "Ngarchelong", "Ngardmau", "Ngaremlengui",
                "Ngatpang", "Ngchesar", "Ngerchelong", "Ngiwal", "Peleliu", "Sonsorol"
            )
        )
        put(
            "Palestine", listOf(
                "Bethlehem", "Deir al-Balah", "Gaza", "Hebron", "Jenin", "Jericho",
                "Jerusalem", "Khan Yunis", "Nablus", "North Gaza", "Qalqilya", "Rafah",
                "Ramallah and Al-Bireh", "Salfit", "Tubas", "Tulkarm"
            )
        )
        put(
            "Panama", listOf(
                "Bocas del Toro", "Chiriquí", "Coclé", "Colón", "Darién", "Emberá-Wounaan",
                "Guna Yala", "Herrera", "Los Santos", "Ngäbe-Buglé", "Panamá",
                "Panamá Oeste", "Veraguas"
            )
        )
        put(
            "Papua New Guinea", listOf(
                "Bougainville", "Central", "Chimbu", "East New Britain", "East Sepik",
                "Eastern Highlands", "Enga", "Gulf", "Hela", "Jiwaka", "Madang",
                "Manus", "Milne Bay", "Morobe", "National Capital District",
                "New Ireland", "Northern", "Sandaun", "Southern Highlands", "Western",
                "Western Highlands", "West New Britain"
            )
        )
        put(
            "Paraguay", listOf(
                "Alto Paraguay", "Alto Paraná", "Amambay", "Asunción", "Boquerón",
                "Caaguazú", "Caazapá", "Canindeyú", "Central", "Concepción",
                "Cordillera", "Guairá", "Itapúa", "Misiones", "Ñeembucú", "Paraguarí",
                "Presidente Hayes", "San Pedro"
            )
        )
        put(
            "Peru", listOf(
                "Amazonas", "Áncash", "Apurímac", "Arequipa", "Ayacucho", "Cajamarca",
                "Callao", "Cusco", "Huancavelica", "Huánuco", "Ica", "Junín",
                "La Libertad", "Lambayeque", "Lima", "Lima Province", "Loreto",
                "Madre de Dios", "Moquegua", "Pasco", "Piura", "Puno", "San Martín",
                "Tacna", "Tumbes", "Ucayali"
            )
        )
        put(
            "Philippines", listOf(
                "Abra", "Agusan del Norte", "Agusan del Sur", "Aklan", "Albay",
                "Antique", "Apayao", "Aurora", "Basilan", "Bataan", "Batanes",
                "Batangas", "Benguet", "Biliran", "Bohol", "Bukidnon", "Bulacan",
                "Cagayan", "Camarines Norte", "Camarines Sur", "Camiguin", "Capiz",
                "Catanduanes", "Cavite", "Cebu", "Cotabato", "Davao de Oro",
                "Davao del Norte", "Davao del Sur", "Davao Occidental", "Davao Oriental",
                "Dinagat Islands", "Eastern Samar", "Guimaras", "Ifugao", "Ilocos Norte",
                "Ilocos Sur", "Iloilo", "Isabela", "Kalinga", "La Union", "Laguna",
                "Lanao del Norte", "Lanao del Sur", "Leyte", "Maguindanao del Norte",
                "Maguindanao del Sur", "Marinduque", "Masbate", "Misamis Occidental",
                "Misamis Oriental", "Mountain Province", "National Capital Region",
                "Negros Occidental", "Negros Oriental", "Northern Samar",
                "Nueva Ecija", "Nueva Vizcaya", "Occidental Mindoro", "Oriental Mindoro",
                "Palawan", "Pampanga", "Pangasinan", "Quezon", "Quirino", "Rizal",
                "Romblon", "Samar", "Sarangani", "Siquijor", "Sorsogon",
                "South Cotabato", "Southern Leyte", "Sultan Kudarat", "Sulu",
                "Surigao del Norte", "Surigao del Sur", "Tarlac", "Tawi-Tawi",
                "Zambales", "Zamboanga del Norte", "Zamboanga del Sur",
                "Zamboanga Sibugay"
            )
        )
        put(
            "Poland", listOf(
                "Greater Poland", "Kuyavian-Pomeranian", "Lesser Poland", "Łódź",
                "Lublin", "Lubusz", "Lower Silesian", "Masovian", "Opole",
                "Podlaskie", "Pomeranian", "Silesian", "Subcarpathian",
                "Świętokrzyskie", "Warmian-Masurian", "West Pomeranian"
            )
        )
        put(
            "Portugal", listOf(
                "Açores", "Alentejo", "Algarve", "Centro", "Lisbon", "Madeira",
                "Norte"
            )
        )
        put(
            "Qatar", listOf(
                "Al Daayen", "Al Khor", "Al Rayyan", "Al Shahaniya", "Al Shamal",
                "Al Wakrah", "Doha", "Umm Salal"
            )
        )
        put(
            "Romania", listOf(
                "Alba", "Arad", "Argeș", "Bacău", "Bihor", "Bistrița-Năsăud",
                "Botoșani", "Brașov", "Brăila", "Bucharest", "Buzău", "Călărași",
                "Caraș-Severin", "Cluj", "Constanța", "Covasna", "Dâmbovița", "Dolj",
                "Galați", "Giurgiu", "Gorj", "Harghita", "Hunedoara", "Ialomița",
                "Iași", "Ilfov", "Maramureș", "Mehedinți", "Mureș", "Neamț", "Olt",
                "Prahova", "Sălaj", "Satu Mare", "Sibiu", "Suceava", "Teleorman",
                "Timiș", "Tulcea", "Vaslui", "Vâlcea", "Vrancea"
            )
        )
        put(
            "Russia", listOf(
                "Adygea", "Altai Krai", "Altai Republic", "Amur", "Arkhangelsk",
                "Astrakhan", "Bashkortostan", "Belgorod", "Bryansk", "Buryatia",
                "Chechnya", "Chelyabinsk", "Chukotka", "Chuvashia", "Dagestan",
                "Ingushetia", "Irkutsk", "Ivanovo", "Jewish Autonomous Oblast",
                "Kabardino-Balkaria", "Kaliningrad", "Kalmykia", "Kaluga", "Kamchatka",
                "Karachay-Cherkessia", "Karelia", "Kemerovo", "Khabarovsk", "Khakassia",
                "Khanty-Mansi", "Kirov", "Komi", "Kostroma", "Krasnodar",
                "Krasnoyarsk", "Kurgan", "Kursk", "Leningrad", "Lipetsk", "Magadan",
                "Mari El", "Mordovia", "Moscow", "Moscow Region", "Murmansk", "Nenets",
                "Nizhny Novgorod", "North Ossetia", "Novgorod", "Novosibirsk", "Omsk",
                "Orenburg", "Oryol", "Penza", "Perm", "Primorsky", "Pskov", "Rostov",
                "Ryazan", "Saint Petersburg", "Sakha", "Sakhalin", "Samara", "Saratov",
                "Smolensk", "Stavropol", "Sverdlovsk", "Tambov", "Tatarstan", "Tomsk",
                "Tula", "Tuva", "Tver", "Tyumen", "Udmurtia", "Ulyanovsk", "Vladimir",
                "Volgograd", "Vologda", "Voronezh", "Yamalo-Nenets", "Yaroslavl",
                "Zabaykalsky"
            )
        )
        put(
            "Rwanda", listOf("Eastern", "Kigali City", "Northern", "Southern", "Western")
        )
        put(
            "Saint Kitts and Nevis", listOf(
                "Christ Church Nichola Town", "Saint Anne Sandy Point",
                "Saint George Basseterre", "Saint George Gingerland",
                "Saint James Windward", "Saint John Capisterre", "Saint John Figtree",
                "Saint Mary Cayon", "Saint Paul Capisterre", "Saint Paul Charlestown",
                "Saint Peter Basseterre", "Saint Thomas Lowland",
                "Saint Thomas Middle Island", "Trinity Palmetto Point"
            )
        )
        put(
            "Saint Lucia", listOf(
                "Anse la Raye", "Castries", "Choiseul", "Dauphin", "Dennery",
                "Gros Islet", "Laborie", "Micoud", "Soufrière", "Vieux Fort"
            )
        )
        put(
            "Saint Vincent and the Grenadines", listOf(
                "Charlotte", "Grenadines", "Saint Andrew", "Saint David",
                "Saint George", "Saint Patrick"
            )
        )
        put(
            "Samoa", listOf(
                "A'ana", "Aiga-i-le-Tai", "Atua", "Fa'asaleleaga", "Gaga'emauga",
                "Gaga'ifomauga", "Palauli", "Satupa'itea", "Tuamasaga",
                "Va'a-o-Fonoti", "Vaisigano"
            )
        )
        put(
            "San Marino", listOf(
                "Acquaviva", "Borgo Maggiore", "Chiesanuova", "Domagnano", "Faetano",
                "Fiorentino", "Montegiardino", "San Marino", "Serravalle"
            )
        )
        put(
            "São Tomé and Príncipe", listOf(
                "Água Grande", "Cantagalo", "Caué", "Lembá", "Lobata", "Mé-Zóchi",
                "Príncipe"
            )
        )
        put(
            "Saudi Arabia", listOf(
                "Al-Bahah", "Al-Jawf", "Al-Qassim", "Asir", "Eastern Province",
                "Hail", "Jazan", "Madinah", "Makkah", "Najran", "Northern Borders",
                "Riyadh", "Tabuk"
            )
        )
        put(
            "Senegal", listOf(
                "Dakar", "Diourbel", "Fatick", "Kaffrine", "Kaolack", "Kédougou",
                "Kolda", "Louga", "Matam", "Saint-Louis", "Sédhiou", "Tambacounda",
                "Thiès", "Ziguinchor"
            )
        )
        put(
            "Serbia", listOf(
                "Belgrade", "Bor", "Braničevo", "Central Banat", "Jablanica",
                "Kolubara", "Mačva", "Moravica", "Nišava", "North Bačka",
                "North Banat", "Pčinja", "Pirot", "Podunavlje", "Pomoravlje",
                "Rasina", "Raška", "South Bačka", "South Banat", "Srem", "Šumadija",
                "Toplica", "Vojvodina", "West Bačka", "Zaječar", "Zlatibor"
            )
        )
        put(
            "Seychelles", listOf(
                "Anse Boileau", "Anse Etoile", "Anse Royale", "Au Cap",
                "Baie Lazare", "Baie Sainte Anne", "Beau Vallon", "Bel Air",
                "Bel Ombre", "Cascade", "English River", "Glacis",
                "Grand Anse Mahe", "Grand Anse Praslin", "La Digue", "Les Mamelles",
                "Mont Buxton", "Mont Fleuri", "Plaisance", "Pointe La Rue",
                "Port Glaud", "Roche Caiman", "Saint Louis", "Takamaka"
            )
        )
        put(
            "Sierra Leone", listOf(
                "Bo", "Bombali", "Bonthe", "Falaba", "Kailahun", "Kambia", "Karene",
                "Kenema", "Koinadugu", "Kono", "Moyamba", "Port Loko", "Pujehun",
                "Tonkolili", "Western Area"
            )
        )
        put("Singapore", listOf("Singapore"))
        put(
            "Slovakia", listOf(
                "Banská Bystrica", "Bratislava", "Košice", "Nitra", "Prešov",
                "Trenčín", "Trnava", "Žilina"
            )
        )
        put(
            "Slovenia", listOf(
                "Gorenjska", "Goriška", "Jugovzhodna Slovenija", "Koroška", "Mura",
                "Obalno-kraška", "Osrednjeslovenska", "Podravska", "Pomurska",
                "Posavska", "Primorsko-notranjska", "Savinjska", "Zasavska"
            )
        )
        put(
            "Solomon Islands", listOf(
                "Central", "Choiseul", "Guadalcanal", "Isabel", "Makira-Ulawa",
                "Malaita", "Rennell and Bellona", "Temotu", "Western"
            )
        )
        put(
            "Somalia", listOf(
                "Awdal", "Bakool", "Banaadir", "Bari", "Bay", "Galguduud", "Gedo",
                "Hiiraan", "Jubbada Dhexe", "Jubbada Hoose", "Mudug", "Nugaal",
                "Sanaag", "Shabeellaha Dhexe", "Shabeellaha Hoose", "Sool",
                "Togdheer", "Woqooyi Galbeed"
            )
        )
        put(
            "South Africa", listOf(
                "Eastern Cape", "Free State", "Gauteng", "KwaZulu-Natal", "Limpopo",
                "Mpumalanga", "North West", "Northern Cape", "Western Cape"
            )
        )
        put(
            "South Sudan", listOf(
                "Central Equatoria", "Eastern Equatoria", "Jonglei", "Lakes",
                "Northern Bahr el Ghazal", "Unity", "Upper Nile", "Warrap",
                "Western Bahr el Ghazal", "Western Equatoria"
            )
        )
        put(
            "Spain", listOf(
                "Andalusia", "Aragon", "Asturias", "Balearic Islands",
                "Basque Country", "Canary Islands", "Cantabria",
                "Castilla-La Mancha", "Castilla y León", "Catalonia", "Extremadura",
                "Galicia", "La Rioja", "Madrid", "Murcia", "Navarre",
                "Valencian Community"
            )
        )
        put(
            "Sri Lanka", listOf(
                "Central", "Eastern", "North Central", "North Western", "Northern",
                "Sabaragamuwa", "Southern", "Uva", "Western"
            )
        )
        put(
            "Sudan", listOf(
                "Al Jazirah", "Al Qadarif", "Blue Nile", "Central Darfur",
                "East Darfur", "Kassala", "Khartoum", "North Darfur",
                "North Kordofan", "Northern", "Red Sea", "River Nile", "Sennar",
                "South Darfur", "South Kordofan", "West Darfur", "West Kordofan",
                "White Nile"
            )
        )
        put(
            "Suriname", listOf(
                "Brokopondo", "Commewijne", "Coronie", "Marowijne", "Nickerie",
                "Para", "Paramaribo", "Saramacca", "Sipaliwini", "Wanica"
            )
        )
        put(
            "Sweden", listOf(
                "Blekinge", "Dalarna", "Gävleborg", "Gotland", "Halland",
                "Jämtland", "Jönköping", "Kalmar", "Kronoberg", "Norrbotten",
                "Örebro", "Östergötland", "Skåne", "Södermanland", "Stockholm",
                "Uppsala", "Värmland", "Västerbotten", "Västernorrland",
                "Västmanland", "Västra Götaland"
            )
        )
        put(
            "Switzerland", listOf(
                "Aargau", "Appenzell Ausserrhoden", "Appenzell Innerrhoden",
                "Basel-Landschaft", "Basel-Stadt", "Bern", "Fribourg", "Geneva",
                "Glarus", "Graubünden", "Jura", "Lucerne", "Neuchâtel", "Nidwalden",
                "Obwalden", "Schaffhausen", "Schwyz", "Solothurn", "St. Gallen",
                "Thurgau", "Ticino", "Uri", "Valais", "Vaud", "Zug", "Zürich"
            )
        )
        put(
            "Syria", listOf(
                "Aleppo", "Damascus", "Daraa", "Deir ez-Zor", "Hama", "Homs",
                "Idlib", "Latakia", "Quneitra", "Raqqa", "Rif Dimashq", "Suwayda",
                "Tartus"
            )
        )
        put(
            "Taiwan", listOf(
                "Changhua", "Chiayi City", "Chiayi County", "Hsinchu City",
                "Hsinchu County", "Hualien", "Kaohsiung", "Keelung", "Kinmen",
                "Lienchiang", "Miaoli", "Nantou", "New Taipei", "Penghu", "Pingtung",
                "Taichung", "Tainan", "Taipei", "Taitung", "Taoyuan", "Yilan",
                "Yunlin"
            )
        )
        put(
            "Tajikistan", listOf(
                "Dushanbe", "Districts of Republican Subordination",
                "Gorno-Badakhshan", "Khatlon", "Sughd"
            )
        )
        put(
            "Tanzania", listOf(
                "Arusha", "Dar es Salaam", "Dodoma", "Geita", "Iringa", "Kagera",
                "Katavi", "Kigoma", "Kilimanjaro", "Lindi", "Manyara", "Mara",
                "Mbeya", "Morogoro", "Mtwara", "Mwanza", "Njombe", "Pemba North",
                "Pemba South", "Pwani", "Rukwa", "Ruvuma", "Shinyanga", "Simiyu",
                "Singida", "Songwe", "Tabora", "Tanga", "Unguja North",
                "Unguja South", "Zanzibar West"
            )
        )
        put(
            "Thailand", listOf(
                "Amnat Charoen", "Ang Thong", "Bangkok", "Bueng Kan", "Buriram",
                "Chachoengsao", "Chai Nat", "Chaiyaphum", "Chanthaburi", "Chiang Mai",
                "Chiang Rai", "Chonburi", "Chumphon", "Kalasin", "Kamphaeng Phet",
                "Kanchanaburi", "Khon Kaen", "Krabi", "Lampang", "Lamphun", "Loei",
                "Lopburi", "Mae Hong Son", "Maha Sarakham", "Mukdahan", "Nakhon Nayok",
                "Nakhon Pathom", "Nakhon Phanom", "Nakhon Ratchasima", "Nakhon Sawan",
                "Nakhon Si Thammarat", "Nan", "Narathiwat", "Nong Bua Lamphu",
                "Nong Khai", "Nonthaburi", "Pathum Thani", "Pattani", "Phang Nga",
                "Phatthalung", "Phayao", "Phetchabun", "Phetchaburi", "Phichit",
                "Phitsanulok", "Phra Nakhon Si Ayutthaya", "Phrae", "Phuket",
                "Prachinburi", "Prachuap Khiri Khan", "Ranong", "Ratchaburi",
                "Rayong", "Roi Et", "Sa Kaeo", "Sakon Nakhon", "Samut Prakan",
                "Samut Sakhon", "Samut Songkhram", "Saraburi", "Satu", "Sing Buri",
                "Sisaket", "Songkhla", "Sukhothai", "Suphan Buri", "Surat Thani",
                "Surin", "Tak", "Trang", "Trat", "Ubon Ratchathani", "Udon Thani",
                "Uthai Thani", "Uttaradit", "Yala", "Yasothon"
            )
        )
        put(
            "Timor-Leste", listOf(
                "Aileu", "Ainaro", "Baucau", "Bobonaro", "Cova Lima", "Dili",
                "Ermera", "Lautém", "Liquiçá", "Manatuto", "Manufahi", "Oecusse",
                "Viqueque"
            )
        )
        put(
            "Togo", listOf("Centrale", "Kara", "Maritime", "Plateaux", "Savanes")
        )
        put(
            "Tonga", listOf("'Eua", "Ha'apai", "Niuas", "Tongatapu", "Vava'u")
        )
        put(
            "Trinidad and Tobago", listOf(
                "Arima", "Chaguanas", "Couva-Tabaquite-Talparo", "Diego Martin",
                "Mayaro-Rio Claro", "Penal-Debe", "Point Fortin", "Port of Spain",
                "Princes Town", "San Fernando", "San Juan-Laventille", "Sangre Grande",
                "Siparia", "Tobago", "Tunapuna-Piarco"
            )
        )
        put(
            "Tunisia", listOf(
                "Ariana", "Béja", "Ben Arous", "Bizerte", "Gabès", "Gafsa",
                "Jendouba", "Kairouan", "Kasserine", "Kébili", "Kef", "Mahdia",
                "Manouba", "Médenine", "Monastir", "Nabeul", "Sfax", "Sidi Bouzid",
                "Siliana", "Sousse", "Tataouine", "Tozeur", "Tunis", "Zaghouan"
            )
        )
        put(
            "Turkey", listOf(
                "Adana", "Adıyaman", "Afyonkarahisar", "Ağrı", "Aksaray", "Amasya",
                "Ankara", "Antalya", "Ardahan", "Artvin", "Aydın", "Balıkesir",
                "Bartın", "Batman", "Bayburt", "Bilecik", "Bingöl", "Bitlis", "Bolu",
                "Burdur", "Bursa", "Çanakkale", "Çankırı", "Çorum", "Denizli",
                "Diyarbakır", "Düzce", "Edirne", "Elazığ", "Erzincan", "Erzurum",
                "Eskişehir", "Gaziantep", "Giresun", "Gümüşhane", "Hakkari", "Hatay",
                "Iğdır", "Isparta", "İstanbul", "İzmir", "Kahramanmaraş", "Karabük",
                "Karaman", "Kars", "Kastamonu", "Kayseri", "Kilis", "Kırıkkale",
                "Kırklareli", "Kırşehir", "Kocaeli", "Konya", "Kütahya", "Malatya",
                "Manisa", "Mardin", "Mersin", "Muğla", "Muş", "Nevşehir", "Niğde",
                "Ordu", "Osmaniye", "Rize", "Sakarya", "Samsun", "Siirt", "Sinop",
                "Sivas", "Şanlıurfa", "Şırnak", "Tekirdağ", "Tokat", "Trabzon",
                "Tunceli", "Uşak", "Van", "Yalova", "Yozgat", "Zonguldak"
            )
        )
        put(
            "Turkmenistan", listOf(
                "Ahal", "Ashgabat", "Balkan", "Daşoguz", "Lebap", "Mary"
            )
        )
        put(
            "Tuvalu", listOf("Funafuti")
        )
        put(
            "Uganda", listOf("Central Region", "Eastern Region", "Northern Region", "Western Region")
        )
        put(
            "Ukraine", listOf(
                "Autonomous Republic of Crimea", "Cherkasy", "Chernihiv", "Chernivtsi",
                "Dnipropetrovsk", "Donetsk", "Ivano-Frankivsk", "Kharkiv", "Kherson",
                "Khmelnytskyi", "Kirovohrad", "Kyiv", "Kyiv City", "Luhansk", "Lviv",
                "Mykolaiv", "Odesa", "Poltava", "Rivne", "Sumy", "Ternopil",
                "Vinnytsia", "Volyn", "Zakarpattia", "Zaporizhzhia", "Zhytomyr"
            )
        )
        put(
            "United Arab Emirates", listOf(
                "Abu Dhabi", "Ajman", "Dubai", "Fujairah", "Ras Al Khaimah",
                "Sharjah", "Umm Al Quwain"
            )
        )
        put(
            "United Kingdom", listOf(
                "England", "Northern Ireland", "Scotland", "Wales"
            )
        )
        put(
            "United States", listOf(
                "Alabama", "Alaska", "Arizona", "Arkansas", "California", "Colorado",
                "Connecticut", "Delaware", "District of Columbia", "Florida", "Georgia",
                "Hawaii", "Idaho", "Illinois", "Indiana", "Iowa", "Kansas", "Kentucky",
                "Louisiana", "Maine", "Maryland", "Massachusetts", "Michigan",
                "Minnesota", "Mississippi", "Missouri", "Montana", "Nebraska",
                "Nevada", "New Hampshire", "New Jersey", "New Mexico", "New York",
                "North Carolina", "North Dakota", "Ohio", "Oklahoma", "Oregon",
                "Pennsylvania", "Rhode Island", "South Carolina", "South Dakota",
                "Tennessee", "Texas", "Utah", "Vermont", "Virginia", "Washington",
                "West Virginia", "Wisconsin", "Wyoming", "American Samoa", "Guam",
                "Northern Mariana Islands", "Puerto Rico", "US Virgin Islands"
            )
        )
        put(
            "Uruguay", listOf(
                "Artigas", "Canelones", "Cerro Largo", "Colonia", "Durazno", "Flores",
                "Florida", "Lavalleja", "Maldonado", "Montevideo", "Paysandú",
                "Río Negro", "Rivera", "Rocha", "Salto", "San José", "Soriano",
                "Tacuarembó", "Treinta y Tres"
            )
        )
        put(
            "Uzbekistan", listOf(
                "Andijan", "Bukhara", "Fergana", "Jizzakh", "Karakalpakstan",
                "Namangan", "Navoiy", "Qashqadaryo", "Samarkand", "Sirdaryo",
                "Surxondaryo", "Tashkent", "Tashkent Region", "Xorazm"
            )
        )
        put(
            "Vanuatu", listOf("Malampa", "Penama", "Sanma", "Shefa", "Tafea", "Torba")
        )
        put("Vatican City", listOf("Vatican City"))
        put(
            "Venezuela", listOf(
                "Amazonas", "Anzoátegui", "Apure", "Aragua", "Barinas", "Bolívar",
                "Carabobo", "Cojedes", "Delta Amacuro", "Distrito Capital", "Falcón",
                "Guárico", "La Guaira", "Lara", "Mérida", "Miranda", "Monagas",
                "Nueva Esparta", "Portuguesa", "Sucre", "Táchira", "Trujillo",
                "Yaracuy", "Zulia"
            )
        )
        put(
            "Vietnam", listOf(
                "An Giang", "Bà Rịa-Vũng Tàu", "Bắc Giang", "Bắc Kạn", "Bạc Liêu",
                "Bắc Ninh", "Bến Tre", "Bình Định", "Bình Dương", "Bình Phước",
                "Bình Thuận", "Cà Mau", "Cao Bằng", "Đà Nẵng", "Đắk Lắk", "Đắk Nông",
                "Điện Biên", "Đồng Nai", "Đồng Tháp", "Gia Lai", "Hà Giang",
                "Hà Nam", "Hà Nội", "Hà Tĩnh", "Hải Dương", "Hải Phòng", "Hậu Giang",
                "Hòa Bình", "Hưng Yên", "Khánh Hòa", "Kiên Giang", "Kon Tum",
                "Lai Châu", "Lâm Đồng", "Lạng Sơn", "Lào Cai", "Long An", "Nam Định",
                "Nghệ An", "Ninh Bình", "Ninh Thuận", "Phú Thọ", "Phú Yên",
                "Quảng Bình", "Quảng Nam", "Quảng Ngãi", "Quảng Ninh", "Quảng Trị",
                "Sóc Trăng", "Sơn La", "Tây Ninh", "Thái Bình", "Thái Nguyên",
                "Thanh Hóa", "Thừa Thiên Huế", "Tiền Giang", "Trà Vinh",
                "Tuyên Quang", "Vĩnh Long", "Vĩnh Phúc", "Yên Bái"
            )
        )
        put(
            "Yemen", listOf(
                "Abyan", "Aden", "Al Bayda", "Al Hudaydah", "Al Jawf", "Al Mahrah",
                "Al Mahwit", "Amanat Al Asimah", "Amran", "Dhamar", "Hadramawt",
                "Hajjah", "Ibb", "Lahij", "Marib", "Raymah", "Saada", "Sanaa",
                "Shabwah", "Socotra", "Taiz"
            )
        )
        put(
            "Zambia", listOf(
                "Central", "Copperbelt", "Eastern", "Luapula", "Lusaka", "Muchinga",
                "North-Western", "Northern", "Southern", "Western"
            )
        )
        put(
            "Zimbabwe", listOf(
                "Bulawayo", "Harare", "Manicaland", "Mashonaland Central",
                "Mashonaland East", "Mashonaland West", "Masvingo",
                "Matabeleland North", "Matabeleland South", "Midlands"
            )
        )
    }

    /**
     * Returns a sorted (A-Z) list of primary subdivisions for the given country,
     * with "Other" appended as a catch-all.
     */
    fun regionsFor(country: String): List<String> {
        if (country.isBlank() || country == "Other") return listOf("Other")
        val base = REGIONS[country]?.sorted() ?: emptyList()
        return base + "Other"
    }
}