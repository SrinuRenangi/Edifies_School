-- Seed realistic attendance data for testing and verification
DO $$
DECLARE
    v_srivan_id UUID := '4926b3a5-3058-4ead-a6a7-b86d192372c8';
    v_kj_id UUID := '87e90a8f-5913-49c2-b272-41e2d67e0b2e';
    v_att1 UUID := 'a1111111-1111-1111-1111-111111111111';
    v_att2 UUID := 'a2222222-2222-2222-2222-222222222222';
    v_att3 UUID := 'a3333333-3333-3333-3333-333333333333';
    v_att4 UUID := 'a4444444-4444-4444-4444-444444444444';
    v_att5 UUID := 'a5555555-5555-5555-5555-555555555555';
    v_att6 UUID := 'a6666666-6666-6666-6666-666666666666';
BEGIN
    -- Clean any existing for these IDs
    DELETE FROM attendance_photos WHERE attendance_id IN (v_att1, v_att2, v_att3, v_att4, v_att5, v_att6);
    DELETE FROM attendance_corrections WHERE attendance_record_id IN (v_att1, v_att2, v_att3, v_att4, v_att5, v_att6);
    DELETE FROM attendance_records WHERE id IN (v_att1, v_att2, v_att3, v_att4, v_att5, v_att6);

    -- 1. Srivan - 2026-10-24 (Today, Completed)
    INSERT INTO attendance_records (id, employee_id, work_date, check_in_time, check_out_time, status, version, created_at, updated_at, has_check_in_photo, has_check_out_photo, is_corrected, is_manual_entry)
    VALUES (v_att1, v_srivan_id, '2026-10-24', '2026-10-24 03:35:00+00', '2026-10-24 12:05:00+00', 'CHECKED_OUT', 1, NOW(), NOW(), true, true, false, false);

    -- 2. Katherine Johnson - 2026-10-24 (Today, Open Checked-In)
    INSERT INTO attendance_records (id, employee_id, work_date, check_in_time, check_out_time, status, version, created_at, updated_at, has_check_in_photo, has_check_out_photo, is_corrected, is_manual_entry)
    VALUES (v_att2, v_kj_id, '2026-10-24', '2026-10-24 04:00:00+00', NULL, 'CHECKED_IN', 0, NOW(), NOW(), true, false, false, false);

    -- 3. Srivan - 2026-10-23 (Yesterday, Completed)
    INSERT INTO attendance_records (id, employee_id, work_date, check_in_time, check_out_time, status, version, created_at, updated_at, has_check_in_photo, has_check_out_photo, is_corrected, is_manual_entry)
    VALUES (v_att3, v_srivan_id, '2026-10-23', '2026-10-23 03:42:00+00', '2026-10-23 12:30:00+00', 'CHECKED_OUT', 1, NOW(), NOW(), true, true, false, false);

    -- 4. Katherine Johnson - 2026-10-23 (Yesterday, Completed)
    INSERT INTO attendance_records (id, employee_id, work_date, check_in_time, check_out_time, status, version, created_at, updated_at, has_check_in_photo, has_check_out_photo, is_corrected, is_manual_entry)
    VALUES (v_att4, v_kj_id, '2026-10-23', '2026-10-23 03:25:00+00', '2026-10-23 11:40:00+00', 'CHECKED_OUT', 1, NOW(), NOW(), true, false, false, false);

    -- 5. Srivan - 2026-10-22 (Auto-Closed session)
    INSERT INTO attendance_records (id, employee_id, work_date, check_in_time, check_out_time, status, version, created_at, updated_at, has_check_in_photo, has_check_out_photo, is_corrected, is_manual_entry)
    VALUES (v_att5, v_srivan_id, '2026-10-22', '2026-10-22 03:30:00+00', NULL, 'AUTO_CLOSED', 1, NOW(), NOW(), true, false, false, false);

    -- 6. Katherine Johnson - 2026-10-22 (Corrected session with audit)
    INSERT INTO attendance_records (id, employee_id, work_date, check_in_time, check_out_time, status, version, created_at, updated_at, has_check_in_photo, has_check_out_photo, is_corrected, is_manual_entry, last_correction_at)
    VALUES (v_att6, v_kj_id, '2026-10-22', '2026-10-22 03:45:00+00', '2026-10-22 12:15:00+00', 'CHECKED_OUT', 2, NOW(), NOW(), true, true, true, false, '2026-10-22 13:00:00+00');

    -- Insert audit log for record 6
    INSERT INTO attendance_corrections (id, attendance_record_id, employee_id, action_type, previous_check_in_time, corrected_check_in_time, previous_check_out_time, corrected_check_out_time, previous_status, corrected_status, reason, performed_by, created_at, record_version)
    VALUES (
        'c6666666-6666-6666-6666-666666666666',
        v_att6,
        v_kj_id,
        'CORRECT_BOTH',
        '2026-10-22 03:45:00+00',
        '2026-10-22 03:45:00+00',
        NULL,
        '2026-10-22 12:15:00+00',
        'AUTO_CLOSED',
        'CHECKED_OUT',
        'Staff forgot to badge out before shift change. Confirmed via supervisor log.',
        'admin',
        '2026-10-22 13:00:00+00',
        2
    );

    -- Insert photo record for record 1
    INSERT INTO attendance_photos (id, attendance_id, employee_id, photo_type, storage_path, file_name, mime_type, file_size, image_width, image_height, storage_status)
    VALUES (
        'b1111111-1111-1111-1111-111111111111',
        v_att1,
        v_srivan_id,
        'CHECK_IN',
        'test_checkin.jpg',
        'test_checkin.jpg',
        'image/jpeg',
        1024,
        640,
        480,
        'ACTIVE'
    );

    RAISE NOTICE 'Attendance records seeded successfully';
END $$;
