create table cell
(
	cell_id       bigint primary key,
	uplus_cell_id varchar(50) unique,
	area          varchar(20)      not null,
	legal_dong    varchar(30)      not null,
	legal_dong_en varchar(30)      not null,
	center_lat    double precision not null,
	center_lng    double precision not null,
	sw_lat        double precision not null,
	sw_lng        double precision not null,
	ne_lat        double precision not null,
	ne_lng        double precision not null
);

alter table cell enable row level security;
