INSERT INTO users(name) VALUES ('Артём'), ('Иван');
INSERT INTO recycling_types(name,unit,pointsPerKg) VALUES ('Бумага','кг',10), ('Стекло','кг',5);
INSERT INTO collection_points(address,available,dailyMassLimit) VALUES ('Лесная, 10',TRUE,100), ('Мира, 5',TRUE,200);
INSERT INTO point_types VALUES(1,1),(1,2),(2,1),(2,2);
INSERT INTO recycling_batches(userId,pointId,typeId,mass,receivedAt,status) VALUES(1,1,1,2,CURRENT_TIMESTAMP,'ACCEPTED');
INSERT INTO bonus_entries(batchId,points,status) VALUES(1,20,'PENDING');
