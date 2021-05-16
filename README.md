# VackTrack SEE (System for Examining Effectiveness)
The VacTrack SEE  system integrates the REQ and MMM (Multifaceted Mobile Management) components, allowing providers to monitor their enrollment in public vaccine programs, ordering of vaccines, reporting of inventory, and documenting of vaccine spoilage/wastage.
SEE is the source of all data relating to vaccine supply, ordering, tracking, and monitoring in the states of Haiti and serves as a national, confidential, population-based, computerized registry that oversees the rollout & administration of vaccines.
By centralizing and encrypting data from each VacPack distribution team within this large, national database, researchers can demarcate specific hotspots of cases of disease to target the movement. of more vaccines to that particular location. Teams can also evaluate the Average Efficacy Rating (AER) of our vaccination system and store relevant healthcare data in their own unique systems.

# IMMUNIZATION INFORMATION SYSTEM (IIS) ARCHITECTURE
Immunization information systems (IIS) are confidential, population-based, computerized databases that record all immunization doses administered by participating providers to persons residing within a given geopolitical area. Immunization information systems play an important role in creating a comprehensive immunization record for you by consolidating vaccinations administered by different providers into electronic records. These systems also provide clinical decision support, to look at your immunization history and generate a forecast or recommendation of vaccines to administer based upon the Advisory Committee on Immunization Practice (ACIP) recommendations for vaccination. You can then decide whether you would like to receive the suggested vaccinations. If your clinician is already using these systems, patient reminders or recalls for immunizations also can be generated and sent directly to you.
![image](https://user-images.githubusercontent.com/67471222/118348356-4b07e680-b567-11eb-93f6-9d7d95d3ea20.png)

IIS Data Implementation Guide Administration site data to be collected and reported includes location, type, address, and data. Vaccine data must be captured, such as vaccine IoT number, vaccine manufacturer, and expiration date. Electronic health records may leverage 2D barcoding technology to improve workflow and vaccine information data quality. Vaccine recipient data must be captured, such as demographics, race, ethnicity, address, date of birth, name, sex, IIS recipient ID, vaccination event ID, administering site (on the body), Consider leveraging existing electronic reporting capabilities from electronic health records or HIE gateways for timely reporting and data quality

![image](https://user-images.githubusercontent.com/67471222/118383259-e5c0fd80-b619-11eb-9d1f-50d34531e38f.png)

IIS:

    1)Provide unidirectional or bidirectional data transmissions for providers
    2)Connect with local/state Health Information Exchanges
    3)Connect to the IZ Gateway to share and receive information from national providers and other IISs


# COVID-19 Clearinghouse (a secure data lake)
The COVID-19 Data Clearinghouse is a cloud-hosted data repository that receives, deduplicates, and deidentifies COVID-19 vaccination data that are then used to populate the IZ Data Lake with deidentified data for analytics.

The COVID-19 Data Clearinghouse allows healthcare providers to search for a patient, see what brand of COVID-19 vaccine they received, and see when they received their first dose of COVID-19 vaccine to ensure dose matching and appropriate vaccination intervals to complete the vaccine series.

![image](https://user-images.githubusercontent.com/67471222/118409571-a2fa3680-b6a8-11eb-87cd-cf7aab8c9475.png)







