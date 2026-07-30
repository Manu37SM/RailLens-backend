package com.labs.train.train_db.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.labs.train.train_db.service.SmartSearchQueryParser.FewerThanHalts;
import com.labs.train.train_db.service.SmartSearchQueryParser.FromTo;
import com.labs.train.train_db.service.SmartSearchQueryParser.LongerThanHours;
import com.labs.train.train_db.service.SmartSearchQueryParser.LongerThanKm;
import com.labs.train.train_db.service.SmartSearchQueryParser.MoreThanHalts;
import com.labs.train.train_db.service.SmartSearchQueryParser.ParsedQuery;
import com.labs.train.train_db.service.SmartSearchQueryParser.ShorterThanHours;
import com.labs.train.train_db.service.SmartSearchQueryParser.ShorterThanKm;
import com.labs.train.train_db.service.SmartSearchQueryParser.StopsAt;
import com.labs.train.train_db.service.SmartSearchQueryParser.StopsAtBoth;

class SmartSearchQueryParserTest {

        @Test
        void parsesStopsAtBothBeforeItCouldBeMisreadAsStopsAt() {

                ParsedQuery result = SmartSearchQueryParser.parse("trains that stop at both NDLS and HWH");

                assertThat(result).isInstanceOf(StopsAtBoth.class);
                StopsAtBoth stopsAtBoth = (StopsAtBoth) result;
                assertThat(stopsAtBoth.stationA()).isEqualTo("NDLS");
                assertThat(stopsAtBoth.stationB()).isEqualTo("HWH");
        }

        @Test
        void parsesSingleStopsAt() {

                ParsedQuery result = SmartSearchQueryParser.parse("trains that stop at New Delhi");

                assertThat(result).isInstanceOf(StopsAt.class);
                assertThat(((StopsAt) result).station()).isEqualTo("New Delhi");
        }

        @Test
        void parsesFromTo() {

                ParsedQuery result = SmartSearchQueryParser.parse("trains from NDLS to HWH");

                assertThat(result).isInstanceOf(FromTo.class);
                FromTo fromTo = (FromTo) result;
                assertThat(fromTo.from()).isEqualTo("NDLS");
                assertThat(fromTo.to()).isEqualTo("HWH");
        }

        @Test
        void parsesLongerAndShorterThanHours() {

                assertThat(SmartSearchQueryParser.parse("trains longer than 10 hours"))
                                .isInstanceOf(LongerThanHours.class);

                ParsedQuery shorter = SmartSearchQueryParser.parse("trains shorter than 2 hrs");
                assertThat(shorter).isInstanceOf(ShorterThanHours.class);
                assertThat(((ShorterThanHours) shorter).hours()).isEqualTo(2);
        }

        @Test
        void parsesLongerAndShorterThanKm() {

                ParsedQuery longer = SmartSearchQueryParser.parse("trains longer than 500km");
                assertThat(longer).isInstanceOf(LongerThanKm.class);
                assertThat(((LongerThanKm) longer).km()).isEqualTo(500);

                assertThat(SmartSearchQueryParser.parse("trains shorter than 100 km"))
                                .isInstanceOf(ShorterThanKm.class);
        }

        @Test
        void parsesHaltCounts() {

                ParsedQuery more = SmartSearchQueryParser.parse("trains with more than 5 halts");
                assertThat(more).isInstanceOf(MoreThanHalts.class);
                assertThat(((MoreThanHalts) more).halts()).isEqualTo(5);

                assertThat(SmartSearchQueryParser.parse("trains with fewer than 2 halts"))
                                .isInstanceOf(FewerThanHalts.class);
        }

        @Test
        void returnsNullForAnUnrecognizedQuery() {

                assertThat(SmartSearchQueryParser.parse("what is the weather today")).isNull();
        }

        @Test
        void isCaseInsensitive() {

                assertThat(SmartSearchQueryParser.parse("TRAINS FROM ndls TO hwh")).isInstanceOf(FromTo.class);
        }
}
