import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Grades } from '../directors-group/grades';

describe('Grades', () => {
  let component: Grades;
  let fixture: ComponentFixture<Grades>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Grades]
    })
    .compileComponents();

    fixture = TestBed.createComponent(Grades);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
