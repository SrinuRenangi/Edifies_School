DO $$
DECLARE
    v_srivan_id UUID := 'df3f098a-c379-424b-951a-a8411b24d762';
    v_kj_id UUID := '478954ee-0bf2-4c52-a2d4-958311614246';
    v_att1 UUID := 'a1111111-1111-1111-1111-111111111111';
    v_att2 UUID := 'a2222222-2222-2222-2222-222222222222';
    v_att3 UUID := 'a3333333-3333-3333-3333-333333333333';
    v_att4 UUID := 'a4444444-4444-4444-4444-444444444444';
    v_att5 UUID := 'a5555555-5555-5555-5555-555555555555';
    v_att6 UUID := 'a6666666-6666-6666-6666-666666666666';
BEGIN
    DELETE FROM attendance_photos WHERE attendance_id IN (v_att1, v_att2, v_att3, v_att4, v_att5, v_att6);
    DELETE FROM attendance_corrections WHERE attendance_record_id IN (v_att1, v_att2, v_att3, v_att4, v_att5, v_att6);
    DELETE FROM attendance_records WHERE id IN (v_att1, v_att2, v_att3, v_att4, v_att5, v_att6);

    -- 1. Srivan - Oct 24 (Today, Completed, 8h 30m)
    INSERT INTO attendance_records (id, employee_id, work_date, check_in_time, check_out_time, status, version, created_at, updated_at, has_check_in_photo, has_check_out_photo, is_corrected, is_manual_entry)
    VALUES (v_att1, v_srivan_id, '2026-10-24', '2026-10-24 03:30:00+00', '2026-10-24 12:00:00+00', 'CHECKED_OUT', 1, NOW(), NOW(), true, true, false, false);

    -- 2. Srivan - Oct 23 (Completed, 8h 30m)
    INSERT INTO attendance_records (id, employee_id, work_date, check_in_time, check_out_time, status, version, created_at, updated_at, has_check_in_photo, has_check_out_photo, is_corrected, is_manual_entry)
    VALUES (v_att2, v_srivan_id, '2026-10-23', '2026-10-23 03:30:00+00', '2026-10-23 12:00:00+00', 'CHECKED_OUT', 1, NOW(), NOW(), true, true, false, false);

    -- 3. Srivan - Oct 22 (Incomplete / Open session)
    INSERT INTO attendance_records (id, employee_id, work_date, check_in_time, check_out_time, status, version, created_at, updated_at, has_check_in_photo, has_check_out_photo, is_corrected, is_manual_entry)
    VALUES (v_att3, v_srivan_id, '2026-10-22', '2026-10-22 03:30:00+00', NULL, 'CHECKED_IN', 1, NOW(), NOW(), true, false, false, false);

    -- 4. Katherine Johnson - Oct 24 (Today, Completed, 8h 00m)
    INSERT INTO attendance_records (id, employee_id, work_date, check_in_time, check_out_time, status, version, created_at, updated_at, has_check_in_photo, has_check_out_photo, is_corrected, is_manual_entry)
    VALUES (v_att4, v_kj_id, '2026-10-24', '2026-10-24 03:30:00+00', '2026-10-24 11:30:00+00', 'CHECKED_OUT', 1, NOW(), NOW(), true, true, false, false);

    -- 5. Katherine Johnson - Oct 23 (Completed with correction, 8h 45m)
    INSERT INTO attendance_records (id, employee_id, work_date, check_in_time, check_out_time, status, version, created_at, updated_at, has_check_in_photo, has_check_out_photo, is_corrected, is_manual_entry, last_correction_at)
    VALUES (v_att5, v_kj_id, '2026-10-23', '2026-10-23 03:30:00+00', '2026-10-23 12:15:00+00', 'CHECKED_OUT', 2, NOW(), NOW(), true, true, true, false, '2026-10-23 13:00:00+00');

    -- Insert correction audit
    INSERT INTO attendance_corrections (id, attendance_record_id, employee_id, action_type, previous_check_in_time, corrected_check_in_time, previous_check_out_time, corrected_check_out_time, previous_status, corrected_status, reason, performed_by, created_at, record_version)
    VALUES (
        'c5555555-5555-5555-5555-555555555555',
        v_att5,
        v_kj_id,
        'CORRECT_BOTH',
        '2026-10-23 03:30:00+00',
        '2026-10-23 03:30:00+00',
        NULL,
        '2026-10-23 12:15:00+00',
        'AUTO_CLOSED',
        'CHECKED_OUT',
        'Supervisor verified badge-out punch missed during shift transition.',
        'admin',
        '2026-10-23 13:00:00+00',
        2
    );

    -- 6. Katherine Johnson - Oct 22 (Completed, 8h 00m)
    INSERT INTO attendance_records (id, employee_id, work_date, check_in_time, check_out_time, status, version, created_at, updated_at, has_check_in_photo, has_check_out_photo, is_corrected, is_manual_entry)
    VALUES (v_att6, v_kj_id, '2026-10-22', '2026-10-22 04:00:00+00', '2026-10-22 12:00:00+00', 'CHECKED_OUT', 1, NOW(), NOW(), true, true, false, false);

END $$;
